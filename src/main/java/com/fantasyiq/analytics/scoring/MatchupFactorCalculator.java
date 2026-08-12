package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.DefenseVsPositionStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Uses the opponent's defense_vs_position_stats rank averaged across all
 * completed weeks so far this season -- never the current week's row, which
 * doesn't exist yet for a game that hasn't been played (defense_vs_position_stats
 * is itself computed retrospectively from that week's box scores). Caller is
 * responsible for only passing weeks strictly before the target week (see
 * DefenseVsPositionStatsRepository.findByTeamAndSeasonAndPositionAndWeekLessThanOrderByWeekAsc).
 */
public final class MatchupFactorCalculator {

    static final String FACTOR_TYPE = "MATCHUP";
    private static final BigDecimal WEIGHT = BigDecimal.valueOf(25);

    private MatchupFactorCalculator() {
    }

    public static Optional<FactorResult> calculate(List<DefenseVsPositionStats> priorWeeksForOpponent, String position) {
        if (priorWeeksForOpponent == null || priorWeeksForOpponent.isEmpty()) {
            return Optional.empty();
        }

        double avgRank = priorWeeksForOpponent.stream()
                .mapToInt(DefenseVsPositionStats::getRankPpr)
                .average()
                .orElseThrow();

        // rank 1 = toughest, 32 = easiest -> favorability 0..1, higher is better for the offense
        double favorability = (avgRank - 1) / 31.0;
        BigDecimal factorValue = BigDecimal.valueOf(avgRank).setScale(1, RoundingMode.HALF_UP);
        BigDecimal contribution = BigDecimal.valueOf(favorability).multiply(WEIGHT).setScale(2, RoundingMode.HALF_UP);

        String narrative = String.format(
                "Opponent has allowed the #%.0f ranked fantasy production to %s on average this season",
                avgRank, position);

        return Optional.of(new FactorResult(FACTOR_TYPE, factorValue, WEIGHT, contribution, narrative));
    }
}
