package com.fantasyiq.analytics.trade;

import com.fantasyiq.analytics.scoring.FactorResult;
import com.fantasyiq.analytics.scoring.InjuryFactorCalculator;
import com.fantasyiq.analytics.scoring.SeasonBaselineFactorCalculator;
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
import java.util.stream.Collectors;

/**
 * Rest-of-season trade value, not tied to any single upcoming game. A
 * player's value is what he has averaged this season (the same
 * SeasonBaselineFactorCalculator start/sit starts from) less his injury
 * status's expected cost over the games that remain.
 * MatchupFactorCalculator/VegasImpliedTotalFactorCalculator/WeatherFactorCalculator
 * are deliberately excluded: they answer "how good is this matchup *this
 * week*", which doesn't exist yet for a multi-week trade horizon (no
 * forecast beyond ~5 days, no odds posted that far out, no known future
 * opponent to look up defense stats for).
 *
 * "This season" is the most recent season with any box score. A player with
 * no games in it has no value to report, which is shown as no data rather than
 * guessed from an older season.
 *
 * Computed fresh per request, not cached/persisted like start/sit -- there's
 * no natural "week" to key a cache on. Scoring a whole position to find its
 * replacement level takes a handful of queries (one per position, plus one
 * for injuries), not one or two per player.
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

    /** What every player's value in one request is measured against. */
    private record Basis(Integer season, Map<UUID, String> injuryStatusByPlayer) {
    }

    public TradeAnalysisResult analyze(List<UUID> sideAPlayerIds, List<UUID> sideBPlayerIds) {
        Basis basis = new Basis(playerGameStatsRepository.findLatestSeason().orElse(null), latestInjuryStatuses());
        Map<String, BigDecimal> replacementLevels = computeReplacementLevels(basis);
        TradeSideValue sideA = valueSide(sideAPlayerIds, basis, replacementLevels);
        TradeSideValue sideB = valueSide(sideBPlayerIds, basis, replacementLevels);
        BigDecimal valueDelta = sideA.totalValue().subtract(sideB.totalValue());
        return new TradeAnalysisResult(sideA, sideB, valueDelta);
    }

    private TradeSideValue valueSide(List<UUID> playerIds, Basis basis, Map<String, BigDecimal> replacementLevels) {
        List<PlayerTradeValue> values = playerIds.stream()
                .map(this::findPlayerOrThrow)
                .map(player -> valueForPlayer(player, basis, replacementLevels))
                .toList();
        BigDecimal total = values.stream()
                .map(v -> v.valueAboveReplacement() != null ? v.valueAboveReplacement() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TradeSideValue(values, total);
    }

    private Player findPlayerOrThrow(UUID playerId) {
        return playerRepository.findById(playerId).orElseThrow(() -> new PlayerNotFoundException(playerId));
    }

    private PlayerTradeValue valueForPlayer(Player player, Basis basis, Map<String, BigDecimal> replacementLevels) {
        List<PlayerGameStats> seasonGames = basis.season() == null
                ? List.of()
                : playerGameStatsRepository.findByPlayerAndGame_Season(player, basis.season());
        List<FactorResult> factors = factorsFor(seasonGames, basis.injuryStatusByPlayer().get(player.getId()));
        BigDecimal replacementLevel = replacementLevels.get(player.getPosition());

        if (factors.isEmpty()) {
            return new PlayerTradeValue(player.getId(), player.getFullName(), player.getPosition(),
                    null, replacementLevel, null, factors);
        }

        BigDecimal score = sumContributions(factors);
        BigDecimal valueAboveReplacement = replacementLevel != null ? score.subtract(replacementLevel) : null;

        return new PlayerTradeValue(player.getId(), player.getFullName(), player.getPosition(),
                score, replacementLevel, valueAboveReplacement, factors);
    }

    /** Empty when the player has no games this season: there is nothing to value. */
    private List<FactorResult> factorsFor(List<PlayerGameStats> seasonGames, String injuryStatus) {
        Optional<FactorResult> baseline = SeasonBaselineFactorCalculator.calculate(seasonGames);
        if (baseline.isEmpty()) {
            return List.of();
        }

        List<FactorResult> factors = new ArrayList<>();
        factors.add(baseline.get());
        InjuryFactorCalculator.calculateForRestOfSeason(injuryStatus, baseline.get().contribution())
                .ifPresent(factors::add);
        return factors;
    }

    private BigDecimal sumContributions(List<FactorResult> factors) {
        return factors.stream().map(FactorResult::contribution).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<UUID, String> latestInjuryStatuses() {
        Map<UUID, String> statuses = new HashMap<>();
        for (InjuryReport report : injuryReportRepository.findLatestPerPlayer()) {
            statuses.putIfAbsent(report.getPlayer().getId(), report.getStatus());
        }
        return statuses;
    }

    private Map<String, BigDecimal> computeReplacementLevels(Basis basis) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (basis.season() == null) {
            return result;
        }

        for (Map.Entry<String, Integer> entry : REPLACEMENT_RANK_BY_POSITION.entrySet()) {
            String position = entry.getKey();
            int replacementRank = entry.getValue();

            Map<UUID, List<PlayerGameStats>> gamesByPlayer = playerGameStatsRepository
                    .findBySeasonAndPosition(basis.season(), position).stream()
                    .collect(Collectors.groupingBy(stats -> stats.getPlayer().getId()));

            List<BigDecimal> scoresDescending = gamesByPlayer.entrySet().stream()
                    .map(games -> factorsFor(games.getValue(), basis.injuryStatusByPlayer().get(games.getKey())))
                    .filter(factors -> !factors.isEmpty())
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
