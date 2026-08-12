package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.PlayerGameStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * A proxy for opportunity trend, not a true snap%/target-share signal --
 * ESPN's free tier never gives us snaps/snap_pct (see CLAUDE.md), so this
 * uses raw volume counting stats (targets, rush attempts, pass attempts)
 * instead, which is directionally useful but less precise than a real
 * target-share computation would be.
 */
public final class UsageTrendFactorCalculator {

    static final String FACTOR_TYPE = "USAGE";
    private static final double SCALE = 1.5;
    private static final double MAX_MAGNITUDE = 20;
    private static final int RECENT_WINDOW = 2;
    private static final int PRIOR_WINDOW = 2;

    private UsageTrendFactorCalculator() {
    }

    /**
     * @param priorGamesDescending games from before the target week, most
     *                             recent first (see PlayerGameStatsRepository
     *                             .findByPlayerAndGame_SeasonAndGame_WeekLessThanOrderByGame_WeekDesc)
     */
    public static Optional<FactorResult> calculate(List<PlayerGameStats> priorGamesDescending, String position) {
        if (priorGamesDescending == null || priorGamesDescending.size() < RECENT_WINDOW + PRIOR_WINDOW) {
            return Optional.empty();
        }

        double recentAvg = priorGamesDescending.subList(0, RECENT_WINDOW).stream()
                .mapToInt(g -> volumeMetric(g, position)).average().orElseThrow();
        double priorAvg = priorGamesDescending.subList(RECENT_WINDOW, RECENT_WINDOW + PRIOR_WINDOW).stream()
                .mapToInt(g -> volumeMetric(g, position)).average().orElseThrow();

        double delta = recentAvg - priorAvg;
        double contributionValue = Math.max(-MAX_MAGNITUDE, Math.min(MAX_MAGNITUDE, delta * SCALE));

        BigDecimal factorValue = BigDecimal.valueOf(delta).setScale(2, RoundingMode.HALF_UP);
        BigDecimal contribution = BigDecimal.valueOf(contributionValue).setScale(2, RoundingMode.HALF_UP);

        String narrative = String.format(
                "Averaging %.1f %s over the last %d games, %s %.1f over the %d before that",
                recentAvg, volumeMetricLabel(position), RECENT_WINDOW,
                delta >= 0 ? "up from" : "down from", priorAvg, PRIOR_WINDOW);

        return Optional.of(new FactorResult(FACTOR_TYPE, factorValue, BigDecimal.valueOf(SCALE), contribution, narrative));
    }

    private static int volumeMetric(PlayerGameStats stats, String position) {
        return switch (position) {
            case "QB" -> nz(stats.getPassingAttempts());
            case "RB" -> nz(stats.getRushAttempts()) + nz(stats.getTargets());
            default -> nz(stats.getTargets());
        };
    }

    private static String volumeMetricLabel(String position) {
        return switch (position) {
            case "QB" -> "pass attempts";
            case "RB" -> "touches";
            default -> "targets";
        };
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
