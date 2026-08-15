package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.PlayerGameStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Added after backtesting the original five factors against the full 2025
 * season showed near-zero correlation with actual output (see CLAUDE.md's
 * "Weight tuning" section) -- investigating why turned up the single
 * strongest signal in the whole dataset: a player's own fantasy points from
 * their most recent prior game correlate with their next game's points at
 * ~0.53 (Pearson), verified independently against Postgres's own corr()/
 * regr_slope() aggregates. None of the original factors captured that
 * signal at all -- UsageTrendFactorCalculator tracks opportunity *volume*
 * (targets/carries), not actual *points* produced.
 *
 * WEIGHT is deliberately set to that same empirically-measured regression
 * slope (~0.53) rather than a hand-picked guess -- the one factor in this
 * engine whose initial weight came directly from real backtest data instead
 * of intuition. Revisit via the same backtest/tune-weights loop once this
 * has real data behind it.
 */
public final class RecentPerformanceFactorCalculator {

    static final String FACTOR_TYPE = "RECENT_PERFORMANCE";
    private static final BigDecimal WEIGHT = BigDecimal.valueOf(0.53);

    private RecentPerformanceFactorCalculator() {
    }

    /**
     * @param priorGamesDescending games from before the target week, most
     *                             recent first (see PlayerGameStatsRepository
     *                             .findByPlayerAndGame_SeasonAndGame_WeekLessThanOrderByGame_WeekDesc)
     *                             -- only the single most recent entry is used,
     *                             matching exactly what was measured in the backtest.
     */
    public static Optional<FactorResult> calculate(List<PlayerGameStats> priorGamesDescending) {
        if (priorGamesDescending == null || priorGamesDescending.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal recentPoints = priorGamesDescending.get(0).getFantasyPointsPpr();
        if (recentPoints == null) {
            return Optional.empty();
        }

        BigDecimal contribution = recentPoints.multiply(WEIGHT).setScale(2, RoundingMode.HALF_UP);
        String narrative = String.format("Scored %.1f PPR points in their most recent game", recentPoints.doubleValue());

        return Optional.of(new FactorResult(FACTOR_TYPE, recentPoints, WEIGHT, contribution, narrative));
    }
}
