package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.DefenseVsPositionStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * A percentage adjustment of the player's baseline: how many more (or fewer)
 * fantasy points the opponent has allowed to this position than the average
 * defense, over all completed weeks before the target week. Never the target
 * week's own row, which doesn't exist yet for a game that hasn't been played
 * (defense_vs_position_stats is itself computed retrospectively).
 *
 * v1 averaged the opponent's league *rank*, which backtested at ~0.00
 * correlation with real output. Points allowed relative to the league average
 * is the same idea without throwing away the size of the gap, and backtesting
 * 2025 found a small positive effect when it is applied at a quarter of its
 * face value (DAMPING): larger weights made predictions worse. Opponents differ
 * by about +-21% (one standard deviation), so the adjustment is typically
 * within +-5% of the baseline.
 */
public final class MatchupFactorCalculator {

    static final String FACTOR_TYPE = "MATCHUP";
    static final BigDecimal DAMPING = new BigDecimal("0.25");

    private MatchupFactorCalculator() {
    }

    /**
     * @param opponentPriorWeeks the opponent's rows for this position before the target week
     * @param leaguePriorWeeks   every team's rows for this position over the same weeks
     * @param baseline           the player's season-average PPR
     */
    public static Optional<FactorResult> calculate(List<DefenseVsPositionStats> opponentPriorWeeks,
                                                   List<DefenseVsPositionStats> leaguePriorWeeks,
                                                   BigDecimal baseline, String position) {
        if (isEmpty(opponentPriorWeeks) || isEmpty(leaguePriorWeeks)) {
            return Optional.empty();
        }

        double opponentAverage = averageAllowed(opponentPriorWeeks);
        double leagueAverage = averageAllowed(leaguePriorWeeks);
        if (leagueAverage <= 0) {
            return Optional.empty();
        }

        double relative = opponentAverage / leagueAverage - 1;
        BigDecimal contribution = baseline.max(BigDecimal.ZERO)
                .multiply(DAMPING)
                .multiply(BigDecimal.valueOf(relative))
                .setScale(2, RoundingMode.HALF_UP);

        String narrative = String.format("Opponent has allowed %.0f%% %s fantasy points to %s than the average defense this season",
                Math.abs(relative) * 100, relative >= 0 ? "more" : "fewer", position);

        return Optional.of(new FactorResult(FACTOR_TYPE,
                BigDecimal.valueOf(relative).setScale(4, RoundingMode.HALF_UP), DAMPING, contribution, narrative));
    }

    private static boolean isEmpty(List<DefenseVsPositionStats> rows) {
        return rows == null || rows.isEmpty();
    }

    private static double averageAllowed(List<DefenseVsPositionStats> rows) {
        return rows.stream()
                .mapToDouble(r -> r.getFantasyPointsAllowedPpr().doubleValue())
                .average()
                .orElseThrow();
    }
}
