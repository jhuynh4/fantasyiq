package com.fantasyiq.analytics.backtest;

import com.fantasyiq.analytics.startsit.StartSitRecommendationService;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationRepository;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final GameRepository gameRepository;
    private final StartSitRecommendationService startSitRecommendationService;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationMatcher recommendationMatcher;

    public BacktestService(GameRepository gameRepository,
                            StartSitRecommendationService startSitRecommendationService,
                            RecommendationRepository recommendationRepository,
                            RecommendationMatcher recommendationMatcher) {
        this.gameRepository = gameRepository;
        this.startSitRecommendationService = startSitRecommendationService;
        this.recommendationRepository = recommendationRepository;
        this.recommendationMatcher = recommendationMatcher;
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
        RecommendationMatcher.MatchResult matchResult = recommendationMatcher.match(recommendations, season);

        List<Double> predicted = new ArrayList<>();
        List<Double> actual = new ArrayList<>();
        Map<String, List<Double>> predictedByPosition = new LinkedHashMap<>();
        Map<String, List<Double>> actualByPosition = new LinkedHashMap<>();

        for (MatchedRecommendation match : matchResult.matched()) {
            double predictedScore = match.recommendation().getScore().doubleValue();
            predicted.add(predictedScore);
            actual.add(match.actualPoints());

            String position = match.recommendation().getPlayer().getPosition();
            predictedByPosition.computeIfAbsent(position, p -> new ArrayList<>()).add(predictedScore);
            actualByPosition.computeIfAbsent(position, p -> new ArrayList<>()).add(match.actualPoints());
        }

        Map<String, Double> correlationByPosition = new LinkedHashMap<>();
        for (String position : predictedByPosition.keySet()) {
            correlationByPosition.put(position,
                    PearsonCorrelation.of(predictedByPosition.get(position), actualByPosition.get(position)));
        }

        return new BacktestResult(season, weeksEvaluated, recommendations.size(),
                matchResult.excludedDueToInjuryOverride(), predicted.size(),
                PearsonCorrelation.of(predicted, actual), correlationByPosition);
    }
}
