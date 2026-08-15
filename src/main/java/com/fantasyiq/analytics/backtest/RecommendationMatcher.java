package com.fantasyiq.analytics.backtest;

import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared by BacktestService and WeightTuningService -- both need the same
 * "pair each recommendation with what actually happened" step before doing
 * their own separate analysis on top of it.
 */
@Component
class RecommendationMatcher {

    private static final BigDecimal INJURY_OVERRIDE_THRESHOLD = BigDecimal.valueOf(-100);

    private final PlayerGameStatsRepository playerGameStatsRepository;

    RecommendationMatcher(PlayerGameStatsRepository playerGameStatsRepository) {
        this.playerGameStatsRepository = playerGameStatsRepository;
    }

    record MatchResult(List<MatchedRecommendation> matched, int excludedDueToInjuryOverride) {
    }

    MatchResult match(List<Recommendation> recommendations, int season) {
        List<MatchedRecommendation> matched = new ArrayList<>();
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

            matched.add(new MatchedRecommendation(recommendation, actualStats.get().getFantasyPointsPpr().doubleValue()));
        }

        return new MatchResult(matched, excludedDueToInjuryOverride);
    }

    private boolean hasInjuryOverride(Recommendation recommendation) {
        return recommendation.getFactors().stream()
                .anyMatch(f -> "INJURY".equals(f.getFactorType())
                        && f.getContribution().compareTo(INJURY_OVERRIDE_THRESHOLD) < 0);
    }
}
