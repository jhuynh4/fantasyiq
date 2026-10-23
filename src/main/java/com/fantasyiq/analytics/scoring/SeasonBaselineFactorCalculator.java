package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.PlayerGameStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The starting point of a v2 start/sit score: what the player has averaged so
 * far this season, in PPR points. Every other v2 factor is a percentage
 * adjustment of this number, so the final score reads as an expected-points
 * forecast rather than an index.
 *
 * Chosen from backtesting 2025: a season-to-date average correlated with the
 * next game's points at ~0.66, against ~0.53 for the single most recent game
 * and ~0.31 for the v1 composite. It needs no minimum number of games -- it
 * already scored 0.5-0.67 per position after a single game -- and shrinking
 * small samples toward the position mean did not improve on it.
 */
public final class SeasonBaselineFactorCalculator {

    static final String FACTOR_TYPE = "BASELINE";
    private static final BigDecimal WEIGHT = BigDecimal.ONE;

    private SeasonBaselineFactorCalculator() {
    }

    /**
     * @param priorGames the player's games this season from before the target
     *                   week, in any order
     */
    public static Optional<FactorResult> calculate(List<PlayerGameStats> priorGames) {
        if (priorGames == null) {
            return Optional.empty();
        }

        List<BigDecimal> points = priorGames.stream()
                .map(PlayerGameStats::getFantasyPointsPpr)
                .filter(p -> p != null)
                .toList();
        if (points.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal average = points.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(points.size()), 2, RoundingMode.HALF_UP);

        String narrative = String.format("Averaging %.1f PPR points over %d %s this season",
                average.doubleValue(), points.size(), points.size() == 1 ? "game" : "games");

        return Optional.of(new FactorResult(FACTOR_TYPE, average, WEIGHT, average, narrative));
    }

    /** How many games the baseline rests on; the basis for a recommendation's confidence. */
    public static int gamesCounted(List<PlayerGameStats> priorGames) {
        if (priorGames == null) {
            return 0;
        }
        return (int) priorGames.stream().filter(g -> g.getFantasyPointsPpr() != null).count();
    }
}
