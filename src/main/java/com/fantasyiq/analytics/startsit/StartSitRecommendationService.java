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
import com.fantasyiq.domain.stats.InjuryReportRepository;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.domain.stats.WeatherForecast;
import com.fantasyiq.domain.stats.WeatherForecastRepository;
import com.fantasyiq.domain.team.Team;
import jakarta.persistence.EntityManager;
import org.hibernate.FlushMode;
import org.hibernate.Session;
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
    private final EntityManager entityManager;

    public StartSitRecommendationService(GameRepository gameRepository, PlayerRepository playerRepository,
                                          DefenseVsPositionStatsRepository defenseVsPositionStatsRepository,
                                          BettingLineRepository bettingLineRepository,
                                          WeatherForecastRepository weatherForecastRepository,
                                          InjuryReportRepository injuryReportRepository,
                                          PlayerGameStatsRepository playerGameStatsRepository,
                                          RecommendationReconciliationService recommendationReconciliationService,
                                          RecommendationRepository recommendationRepository,
                                          RecommendationCacheService recommendationCacheService,
                                          EntityManager entityManager) {
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
        this.entityManager = entityManager;
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

    /**
     * Everything a week's scoring needs that does not depend on the player, fetched once instead of once per
     * player: this season's points before the week and last season's points, each player's latest injury
     * status, and each defense's history.
     */
    private record WeekInputs(Map<String, DefenseHistory> defenseByPosition,
                              Map<UUID, List<BigDecimal>> thisSeasonPoints,
                              Map<UUID, List<BigDecimal>> lastSeasonPoints,
                              Map<UUID, String> injuryStatus) {
    }

    /**
     * Hibernate normally checks every object it is tracking for changes before each query, so a run of
     * thousands of queries over a growing set of objects spends nearly all of its time doing that (profiled:
     * every sample of the request thread was inside the change check, and the database accounted for ~1 of
     * ~16 seconds). The check is switched off for this method's transaction, and flushed by hand at the two
     * points that read back what was just written.
     */
    @Transactional
    public int computeForWeek(int season, int week) {
        Session session = entityManager.unwrap(Session.class);
        FlushMode previousFlushMode = session.getHibernateFlushMode();
        session.setHibernateFlushMode(FlushMode.COMMIT);
        try {
            return scoreWeek(season, week);
        } finally {
            session.setHibernateFlushMode(previousFlushMode);
        }
    }

    private int scoreWeek(int season, int week) {
        WeekInputs inputs = loadWeekInputs(season, week);

        Set<UUID> scoredPlayerIds = new HashSet<>();
        for (Game game : gameRepository.findBySeasonAndWeek(season, week)) {
            Optional<WeatherForecast> forecast = weatherForecastRepository.findByGame(game);
            scoreTeam(game, game.getHomeTeam(), game.getAwayTeam(), forecast, season, week, inputs, scoredPlayerIds);
            scoreTeam(game, game.getAwayTeam(), game.getHomeTeam(), forecast, season, week, inputs, scoredPlayerIds);
        }

        entityManager.flush();
        removeRecommendationsNoLongerProduced(season, week, scoredPlayerIds);
        entityManager.flush();
        recommendationCacheService.refreshStartSit(season, week);
        return scoredPlayerIds.size();
    }

    private WeekInputs loadWeekInputs(int season, int week) {
        Map<String, DefenseHistory> defenseByPosition = new HashMap<>();
        Map<UUID, List<BigDecimal>> thisSeasonPoints = new HashMap<>();
        Map<UUID, List<BigDecimal>> lastSeasonPoints = new HashMap<>();
        for (String position : SUPPORTED_POSITIONS) {
            defenseByPosition.put(position, DefenseHistory.of(
                    defenseVsPositionStatsRepository.findBySeasonAndPositionAndWeekLessThan(season, position, week)));
            collect(thisSeasonPoints, playerGameStatsRepository
                    .findPointsBySeasonAndPositionAndWeekLessThan(season, position, week));
            collect(lastSeasonPoints, playerGameStatsRepository.findPointsBySeasonAndPosition(season - 1, position));
        }

        Map<UUID, String> injuryStatus = new HashMap<>();
        for (InjuryReportRepository.LatestStatus latest : injuryReportRepository.findLatestStatuses()) {
            injuryStatus.putIfAbsent(latest.getPlayerId(), latest.getStatus());
        }
        return new WeekInputs(defenseByPosition, thisSeasonPoints, lastSeasonPoints, injuryStatus);
    }

    private static void collect(Map<UUID, List<BigDecimal>> into, List<PlayerGameStatsRepository.PlayerPoints> rows) {
        for (PlayerGameStatsRepository.PlayerPoints row : rows) {
            into.computeIfAbsent(row.getPlayerId(), id -> new ArrayList<>()).add(row.getPoints());
        }
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

    private void scoreTeam(Game game, Team offenseTeam, Team defenseTeam, Optional<WeatherForecast> forecast,
                           int season, int week, WeekInputs inputs, Set<UUID> scoredPlayerIds) {
        Optional<BettingLine> line = bettingLineRepository.findByGameAndTeam(game, offenseTeam);
        for (Player player : playerRepository.findByCurrentTeamAndPositionIn(offenseTeam, SUPPORTED_POSITIONS)) {
            List<BigDecimal> thisSeasonPoints = inputs.thisSeasonPoints().getOrDefault(player.getId(), List.of());
            List<BigDecimal> lastSeasonPoints = inputs.lastSeasonPoints().getOrDefault(player.getId(), List.of());
            Optional<FactorResult> baseline =
                    SeasonBaselineFactorCalculator.calculateFromPoints(thisSeasonPoints, lastSeasonPoints);
            if (baseline.isEmpty()) {
                continue;
            }

            List<FactorResult> factors = gatherFactors(baseline.get(), line, forecast, defenseTeam, player,
                    inputs.defenseByPosition().get(player.getPosition()), inputs.injuryStatus().get(player.getId()));

            BigDecimal score = factors.stream().map(FactorResult::contribution).reduce(BigDecimal.ZERO, BigDecimal::add);
            String confidence = determineConfidence(factors,
                    SeasonBaselineFactorCalculator.effectiveGamesFromPoints(thisSeasonPoints, lastSeasonPoints));

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

    private List<FactorResult> gatherFactors(FactorResult baselineFactor, Optional<BettingLine> line,
                                              Optional<WeatherForecast> forecast, Team defenseTeam, Player player,
                                              DefenseHistory defenseHistory, String injuryStatus) {
        List<FactorResult> factors = new ArrayList<>();
        factors.add(baselineFactor);
        BigDecimal baseline = baselineFactor.contribution();
        String position = player.getPosition();

        MatchupFactorCalculator.calculate(defenseHistory.forTeam(defenseTeam), defenseHistory.league(), baseline, position)
                .ifPresent(factors::add);

        VegasImpliedTotalFactorCalculator.calculate(line.map(BettingLine::getImpliedTeamTotal).orElse(null), baseline)
                .ifPresent(factors::add);

        WeatherFactorCalculator.calculate(forecast.orElse(null), position, baseline).ifPresent(factors::add);

        InjuryFactorCalculator.calculate(injuryStatus, baseline).ifPresent(factors::add);

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
