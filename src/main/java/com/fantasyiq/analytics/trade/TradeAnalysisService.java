package com.fantasyiq.analytics.trade;

import com.fantasyiq.analytics.scoring.FactorResult;
import com.fantasyiq.analytics.scoring.InjuryFactorCalculator;
import com.fantasyiq.analytics.scoring.RecentPerformanceFactorCalculator;
import com.fantasyiq.analytics.scoring.UsageTrendFactorCalculator;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerNotFoundException;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.stats.InjuryReport;
import com.fantasyiq.domain.stats.InjuryReportRepository;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rest-of-season trade value, not tied to any single upcoming game --
 * composes only the three factor calculators that don't depend on a
 * specific future opponent (RecentPerformance, UsageTrend, Injury).
 * MatchupFactorCalculator/VegasImpliedTotalFactorCalculator/WeatherFactorCalculator
 * are deliberately excluded: they answer "how good is this matchup *this
 * week*", which doesn't exist yet for a multi-week trade horizon (no
 * forecast beyond ~5 days, no odds posted that far out, no known future
 * opponent to look up defense stats for).
 *
 * Computed fresh per request, not cached/persisted like start/sit --
 * there's no natural "week" to key a cache on, and it's a much cheaper
 * computation (3 factors, no per-game loop across every game in a week).
 */
@Service
public class TradeAnalysisService {

    // Replacement-level ranks match realistic roster-startable cutoffs
    // (12-team league: 1 starting QB/TE, ~2-2.5 starting RB/WR per team).
    private static final Map<String, Integer> REPLACEMENT_RANK_BY_POSITION =
            Map.of("QB", 12, "RB", 24, "WR", 24, "TE", 12);

    private final PlayerRepository playerRepository;
    private final PlayerGameStatsRepository playerGameStatsRepository;
    private final InjuryReportRepository injuryReportRepository;

    public TradeAnalysisService(PlayerRepository playerRepository,
                                 PlayerGameStatsRepository playerGameStatsRepository,
                                 InjuryReportRepository injuryReportRepository) {
        this.playerRepository = playerRepository;
        this.playerGameStatsRepository = playerGameStatsRepository;
        this.injuryReportRepository = injuryReportRepository;
    }

    public TradeAnalysisResult analyze(List<UUID> sideAPlayerIds, List<UUID> sideBPlayerIds) {
        Map<String, BigDecimal> replacementLevels = computeReplacementLevels();
        TradeSideValue sideA = valueSide(sideAPlayerIds, replacementLevels);
        TradeSideValue sideB = valueSide(sideBPlayerIds, replacementLevels);
        BigDecimal valueDelta = sideA.totalValue().subtract(sideB.totalValue());
        return new TradeAnalysisResult(sideA, sideB, valueDelta);
    }

    private TradeSideValue valueSide(List<UUID> playerIds, Map<String, BigDecimal> replacementLevels) {
        List<PlayerTradeValue> values = playerIds.stream()
                .map(this::findPlayerOrThrow)
                .map(player -> valueForPlayer(player, replacementLevels))
                .toList();
        BigDecimal total = values.stream()
                .map(v -> v.valueAboveReplacement() != null ? v.valueAboveReplacement() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TradeSideValue(values, total);
    }

    private Player findPlayerOrThrow(UUID playerId) {
        return playerRepository.findById(playerId).orElseThrow(() -> new PlayerNotFoundException(playerId));
    }

    private PlayerTradeValue valueForPlayer(Player player, Map<String, BigDecimal> replacementLevels) {
        List<FactorResult> factors = gatherFactors(player);
        if (factors.isEmpty()) {
            return new PlayerTradeValue(player.getId(), player.getFullName(), player.getPosition(),
                    null, replacementLevels.get(player.getPosition()), null, factors);
        }

        BigDecimal score = sumContributions(factors);
        BigDecimal replacementLevel = replacementLevels.get(player.getPosition());
        BigDecimal valueAboveReplacement = replacementLevel != null ? score.subtract(replacementLevel) : null;

        return new PlayerTradeValue(player.getId(), player.getFullName(), player.getPosition(),
                score, replacementLevel, valueAboveReplacement, factors);
    }

    private List<FactorResult> gatherFactors(Player player) {
        List<FactorResult> factors = new ArrayList<>();

        List<PlayerGameStats> recentGames =
                playerGameStatsRepository.findTop4ByPlayerOrderByGame_SeasonDescGame_WeekDesc(player);
        RecentPerformanceFactorCalculator.calculate(recentGames).ifPresent(factors::add);
        UsageTrendFactorCalculator.calculate(recentGames, player.getPosition()).ifPresent(factors::add);

        Optional<InjuryReport> injury = injuryReportRepository.findTopByPlayerOrderByReportDateDesc(player);
        InjuryFactorCalculator.calculate(injury.map(InjuryReport::getStatus).orElse(null)).ifPresent(factors::add);

        return factors;
    }

    private BigDecimal sumContributions(List<FactorResult> factors) {
        return factors.stream().map(FactorResult::contribution).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> computeReplacementLevels() {
        Map<String, BigDecimal> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : REPLACEMENT_RANK_BY_POSITION.entrySet()) {
            String position = entry.getKey();
            int replacementRank = entry.getValue();

            List<BigDecimal> scoresDescending = playerRepository.findByPosition(position).stream()
                    .map(this::gatherFactors)
                    .filter(f -> !f.isEmpty())
                    .map(this::sumContributions)
                    .sorted(Comparator.reverseOrder())
                    .toList();

            if (!scoresDescending.isEmpty()) {
                int index = Math.min(replacementRank, scoresDescending.size()) - 1;
                result.put(position, scoresDescending.get(index));
            }
        }
        return result;
    }
}
