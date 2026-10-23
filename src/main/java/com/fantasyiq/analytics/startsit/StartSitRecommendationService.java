package com.fantasyiq.analytics.startsit;

import com.fantasyiq.analytics.scoring.FactorResult;
import com.fantasyiq.analytics.scoring.InjuryFactorCalculator;
import com.fantasyiq.analytics.scoring.MatchupFactorCalculator;
import com.fantasyiq.analytics.scoring.SeasonBaselineFactorCalculator;
import com.fantasyiq.analytics.scoring.VegasImpliedTotalFactorCalculator;
import com.fantasyiq.analytics.scoring.WeatherFactorCalculator;
import com.fantasyiq.cache.RecommendationCacheService;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationFactor;
import com.fantasyiq.domain.recommendation.RecommendationReconciliationService;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.stats.BettingLine;
import com.fantasyiq.domain.stats.BettingLineRepository;
import com.fantasyiq.domain.stats.DefenseVsPositionStats;
import com.fantasyiq.domain.stats.DefenseVsPositionStatsRepository;
import com.fantasyiq.domain.stats.InjuryReport;
import com.fantasyiq.domain.stats.InjuryReportRepository;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.domain.stats.WeatherForecast;
import com.fantasyiq.domain.stats.WeatherForecastRepository;
import com.fantasyiq.domain.team.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Composes the shared factor calculators (analytics.scoring) into a single
 * START_SIT recommendation per player, for every QB/RB/WR/TE on either side
 * of every game in the target week.
 *
 * The score is an expected-points forecast (scoring version v2.1): the
 * player's average PPR (BASELINE, this season's games with last season's
 * average counting as a few extra games) plus percentage adjustments for the
 * opponent, the betting market, the weather and injury status, all summed. A
 * player with no games this season or last has no baseline, and the
 * adjustments mean nothing without one, so that player gets no recommendation
 * row at all rather than a hollow one. The other factors are independently
 * optional: a missing one contributes nothing, and does not lower the score.
 */
@Service
public class StartSitRecommendationService {

    static final String TYPE = "START_SIT";
    static final String SCORING_VERSION = "v2.1";

    private static final List<String> SUPPORTED_POSITIONS = List.of("QB", "RB", "WR", "TE");
    private static final BigDecimal INJURY_OVERRIDE_THRESHOLD = BigDecimal.valueOf(-100);
    private static final int HIGH_CONFIDENCE_GAMES = 6;
    private static final int MEDIUM_CONFIDENCE_GAMES = 3;

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final DefenseVsPositionStatsRepository defenseVsPositionStatsRepository;
    private final BettingLineRepository bettingLineRepository;
    private final WeatherForecastRepository weatherForecastRepository;
    private final InjuryReportRepository injuryReportRepository;
    private final PlayerGameStatsRepository playerGameStatsRepository;
    private final RecommendationReconciliationService recommendationReconciliationService;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationCacheService recommendationCacheService;

    public StartSitRecommendationService(GameRepository gameRepository, PlayerRepository playerRepository,
                                          DefenseVsPositionStatsRepository defenseVsPositionStatsRepository,
                                          BettingLineRepository bettingLineRepository,
                                          WeatherForecastRepository weatherForecastRepository,
                                          InjuryReportRepository injuryReportRepository,
                                          PlayerGameStatsRepository playerGameStatsRepository,
                                          RecommendationReconciliationService recommendationReconciliationService,
                                          RecommendationRepository recommendationRepository,
                                          RecommendationCacheService recommendationCacheService) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.defenseVsPositionStatsRepository = defenseVsPositionStatsRepository;
        this.bettingLineRepository = bettingLineRepository;
        this.weatherForecastRepository = weatherForecastRepository;
        this.injuryReportRepository = injuryReportRepository;
        this.playerGameStatsRepository = playerGameStatsRepository;
        this.recommendationReconciliationService = recommendationReconciliationService;
        this.recommendationRepository = recommendationRepository;
        this.recommendationCacheService = recommendationCacheService;
    }

    /** Every team's defense-versus-position rows before the target week, for one position. */
    private record DefenseHistory(List<DefenseVsPositionStats> league,
                                  Map<Integer, List<DefenseVsPositionStats>> byTeamId) {

        static DefenseHistory of(List<DefenseVsPositionStats> rows) {
            Map<Integer, List<DefenseVsPositionStats>> byTeamId = new HashMap<>();
            for (DefenseVsPositionStats row : rows) {
                byTeamId.computeIfAbsent(row.getTeam().getId(), id -> new ArrayList<>()).add(row);
            }
            return new DefenseHistory(rows, byTeamId);
        }

        List<DefenseVsPositionStats> forTeam(Team team) {
            return byTeamId.getOrDefault(team.getId(), List.of());
        }
    }

    @Transactional
    public int computeForWeek(int season, int week) {
        Map<String, DefenseHistory> defenseByPosition = new HashMap<>();
        for (String position : SUPPORTED_POSITIONS) {
            defenseByPosition.put(position, DefenseHistory.of(
                    defenseVsPositionStatsRepository.findBySeasonAndPositionAndWeekLessThan(season, position, week)));
        }

        Map<UUID, List<BigDecimal>> lastSeasonByPlayer = new HashMap<>();
        for (String position : SUPPORTED_POSITIONS) {
            for (PlayerGameStatsRepository.PlayerPoints row
                    : playerGameStatsRepository.findPointsBySeasonAndPosition(season - 1, position)) {
                lastSeasonByPlayer.computeIfAbsent(row.getPlayerId(), id -> new ArrayList<>()).add(row.getPoints());
            }
        }

        Set<UUID> scoredPlayerIds = new HashSet<>();
        for (Game game : gameRepository.findBySeasonAndWeek(season, week)) {
            scoreTeam(game, game.getHomeTeam(), game.getAwayTeam(), season, week, defenseByPosition,
                    lastSeasonByPlayer, scoredPlayerIds);
            scoreTeam(game, game.getAwayTeam(), game.getHomeTeam(), season, week, defenseByPosition,
                    lastSeasonByPlayer, scoredPlayerIds);
        }
        removeRecommendationsNoLongerProduced(season, week, scoredPlayerIds);
        recommendationCacheService.refreshStartSit(season, week);
        return scoredPlayerIds.size();
    }

    /**
     * A run replaces the week's recommendations, not just adds to them: a player
     * who had a row from an earlier run (an older scoring version, or data that
     * has since changed) but no longer qualifies would otherwise stay in the
     * rankings with a stale score alongside the fresh ones.
     */
    private void removeRecommendationsNoLongerProduced(int season, int week, Set<UUID> scoredPlayerIds) {
        List<Recommendation> stale = recommendationRepository.findBySeasonAndWeekAndType(season, week, TYPE).stream()
                .filter(r -> !scoredPlayerIds.contains(r.getPlayer().getId()))
                .toList();
        recommendationRepository.deleteAll(stale);
    }

    private void scoreTeam(Game game, Team offenseTeam, Team defenseTeam, int season, int week,
                           Map<String, DefenseHistory> defenseByPosition,
                           Map<UUID, List<BigDecimal>> lastSeasonByPlayer, Set<UUID> scoredPlayerIds) {
        for (Player player : playerRepository.findByCurrentTeamAndPositionIn(offenseTeam, SUPPORTED_POSITIONS)) {
            List<PlayerGameStats> priorGames = playerGameStatsRepository
                    .findByPlayerAndGame_SeasonAndGame_WeekLessThanOrderByGame_WeekDesc(player, season, week);
            List<BigDecimal> lastSeasonPoints = lastSeasonByPlayer.getOrDefault(player.getId(), List.of());
            Optional<FactorResult> baseline = SeasonBaselineFactorCalculator.calculate(priorGames, lastSeasonPoints);
            if (baseline.isEmpty()) {
                continue;
            }

            List<FactorResult> factors = gatherFactors(baseline.get(), game, offenseTeam, defenseTeam, player,
                    defenseByPosition.get(player.getPosition()));

            BigDecimal score = factors.stream().map(FactorResult::contribution).reduce(BigDecimal.ZERO, BigDecimal::add);
            String confidence = determineConfidence(factors,
                    SeasonBaselineFactorCalculator.effectiveGames(priorGames, lastSeasonPoints));

            Recommendation recommendation = recommendationReconciliationService.resolveOrCreate(
                    player, season, week, TYPE, score, confidence, SCORING_VERSION);

            List<RecommendationFactor> factorEntities = factors.stream()
                    .map(fr -> new RecommendationFactor(recommendation, fr.factorType(), fr.factorValue(),
                            fr.factorWeight(), fr.contribution(), fr.narrative()))
                    .toList();
            recommendation.replaceFactors(factorEntities);
            recommendationRepository.save(recommendation);

            scoredPlayerIds.add(player.getId());
        }
    }

    private List<FactorResult> gatherFactors(FactorResult baselineFactor, Game game, Team offenseTeam,
                                              Team defenseTeam, Player player, DefenseHistory defenseHistory) {
        List<FactorResult> factors = new ArrayList<>();
        factors.add(baselineFactor);
        BigDecimal baseline = baselineFactor.contribution();
        String position = player.getPosition();

        MatchupFactorCalculator.calculate(defenseHistory.forTeam(defenseTeam), defenseHistory.league(), baseline, position)
                .ifPresent(factors::add);

        Optional<BettingLine> line = bettingLineRepository.findByGameAndTeam(game, offenseTeam);
        VegasImpliedTotalFactorCalculator.calculate(line.map(BettingLine::getImpliedTeamTotal).orElse(null), baseline)
                .ifPresent(factors::add);

        Optional<WeatherForecast> forecast = weatherForecastRepository.findByGame(game);
        WeatherFactorCalculator.calculate(forecast.orElse(null), position, baseline).ifPresent(factors::add);

        Optional<InjuryReport> injury = injuryReportRepository.findTopByPlayerOrderByReportDateDesc(player);
        InjuryFactorCalculator.calculate(injury.map(InjuryReport::getStatus).orElse(null), baseline)
                .ifPresent(factors::add);

        return factors;
    }

    /**
     * How much evidence the baseline rests on, in games: this season's plus the
     * weight last season counts for. (v1 counted how many factors had data, which in v2 would say nothing
     * about the forecast's reliability -- it would only reflect whether odds and
     * weather happened to be ingested.) A player ruled out is always LOW.
     */
    private String determineConfidence(List<FactorResult> factors, int gamesPlayed) {
        boolean hasInjuryOverride = factors.stream()
                .anyMatch(f -> "INJURY".equals(f.factorType()) && f.contribution().compareTo(INJURY_OVERRIDE_THRESHOLD) < 0);
        if (hasInjuryOverride) {
            return "LOW";
        }
        if (gamesPlayed >= HIGH_CONFIDENCE_GAMES) {
            return "HIGH";
        }
        if (gamesPlayed >= MEDIUM_CONFIDENCE_GAMES) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
