package com.fantasyiq.analytics.backtest;

import com.fantasyiq.analytics.startsit.StartSitRecommendationService;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the start/sit engine across every week of a completed season, then
 * checks how well predicted scores correlate with what actually happened
 * (real fantasy points from player_game_stats) -- the sanity check the dev
 * plan calls out as Phase 3's last item, closing the loop on "is this
 * engine actually predictive, or just internally consistent."
 */
@Service
public class BacktestService {

    private static final String TYPE = "START_SIT";
    private static final int MAX_REGULAR_SEASON_WEEK = 18;
    private static final BigDecimal INJURY_OVERRIDE_THRESHOLD = BigDecimal.valueOf(-100);

    private final GameRepository gameRepository;
    private final StartSitRecommendationService startSitRecommendationService;
    private final RecommendationRepository recommendationRepository;
    private final PlayerGameStatsRepository playerGameStatsRepository;

    public BacktestService(GameRepository gameRepository,
                            StartSitRecommendationService startSitRecommendationService,
                            RecommendationRepository recommendationRepository,
                            PlayerGameStatsRepository playerGameStatsRepository) {
        this.gameRepository = gameRepository;
        this.startSitRecommendationService = startSitRecommendationService;
        this.recommendationRepository = recommendationRepository;
        this.playerGameStatsRepository = playerGameStatsRepository;
    }

    /**
     * Deliberately NOT @Transactional at this level -- computeForWeek is
     * already @Transactional on its own, and Spring's default propagation
     * would otherwise join all 18 weeks' worth of work (recommendations,
     * factors, reads) into one giant Hibernate session/transaction for the
     * whole method, ballooning dirty-checking cost as the session grows
     * and making the whole run crawl. Each computeForWeek call gets its
     * own transaction instead; the matching/correlation pass afterward is
     * read-only and doesn't need one at all.
     */
    public BacktestResult runBacktest(int season) {
        int weeksEvaluated = 0;
        for (int week = 1; week <= MAX_REGULAR_SEASON_WEEK; week++) {
            if (gameRepository.findBySeasonAndWeek(season, week).isEmpty()) {
                continue;
            }
            startSitRecommendationService.computeForWeek(season, week);
            weeksEvaluated++;
        }

        List<Recommendation> recommendations = recommendationRepository.findBySeasonAndType(season, TYPE);

        List<Double> predicted = new ArrayList<>();
        List<Double> actual = new ArrayList<>();
        Map<String, List<Double>> predictedByPosition = new LinkedHashMap<>();
        Map<String, List<Double>> actualByPosition = new LinkedHashMap<>();
        int excludedDueToInjuryOverride = 0;

        for (Recommendation recommendation : recommendations) {
            if (hasInjuryOverride(recommendation)) {
                excludedDueToInjuryOverride++;
                continue;
            }

            Optional<PlayerGameStats> actualStats = playerGameStatsRepository
                    .findByPlayerAndGame_SeasonAndGame_Week(recommendation.getPlayer(), season, recommendation.getWeek());
            if (actualStats.isEmpty() || actualStats.get().getFantasyPointsPpr() == null) {
                continue;
            }

            double predictedScore = recommendation.getScore().doubleValue();
            double actualPoints = actualStats.get().getFantasyPointsPpr().doubleValue();
            predicted.add(predictedScore);
            actual.add(actualPoints);

            String position = recommendation.getPlayer().getPosition();
            predictedByPosition.computeIfAbsent(position, p -> new ArrayList<>()).add(predictedScore);
            actualByPosition.computeIfAbsent(position, p -> new ArrayList<>()).add(actualPoints);
        }

        Map<String, Double> correlationByPosition = new LinkedHashMap<>();
        for (String position : predictedByPosition.keySet()) {
            correlationByPosition.put(position,
                    PearsonCorrelation.of(predictedByPosition.get(position), actualByPosition.get(position)));
        }

        return new BacktestResult(season, weeksEvaluated, recommendations.size(), excludedDueToInjuryOverride,
                predicted.size(), PearsonCorrelation.of(predicted, actual), correlationByPosition);
    }

    private boolean hasInjuryOverride(Recommendation recommendation) {
        return recommendation.getFactors().stream()
                .anyMatch(f -> "INJURY".equals(f.getFactorType())
                        && f.getContribution().compareTo(INJURY_OVERRIDE_THRESHOLD) < 0);
    }
}
