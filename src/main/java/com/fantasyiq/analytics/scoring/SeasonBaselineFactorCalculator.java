package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.PlayerGameStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The starting point of a score: what the player has averaged, in PPR points.
 * Every other factor is a percentage adjustment of this number, so the final
 * score reads as an expected-points forecast rather than an index.
 *
 * This season's games carry the baseline, with last season's average counting
 * as a few extra games' worth: PRIOR_WEIGHT_GAMES at most, scaled down for a
 * player whose last season was short, and outweighed as this season's games
 * pile up. Before the season has games (week 1) it is last season's average
 * alone; a player with neither has no baseline.
 *
 * Both choices come from backtesting 2025 against 2024:
 * - A season-to-date average correlated with the next game's points at ~0.66
 *   against ~0.53 for the single most recent game, and needs no minimum number
 *   of games (shrinking toward the position mean did not help).
 * - Last season's average alone correlated at 0.70 in week 1, and blending it
 *   in lifted every stretch of the season over using this season alone (weeks
 *   2-3: 0.57 to 0.68; whole season: 0.651 to 0.676). Any weight from 3 to 6
 *   games did about equally well; 4 is in the middle of that plateau.
 */
public final class SeasonBaselineFactorCalculator {

    static final String FACTOR_TYPE = "BASELINE";
    private static final BigDecimal WEIGHT = BigDecimal.ONE;
    static final double PRIOR_WEIGHT_GAMES = 4;
    static final int FULL_SEASON_GAMES = 17;

    private SeasonBaselineFactorCalculator() {
    }

    /** This season's games only, for callers with no use for last season. */
    public static Optional<FactorResult> calculate(List<PlayerGameStats> thisSeasonGames) {
        return calculate(thisSeasonGames, List.of());
    }

    /**
     * @param thisSeasonGames the player's games this season from before the target week, in any order
     * @param lastSeasonPoints the player's PPR points in each game of the season before (may be empty)
     */
    public static Optional<FactorResult> calculate(List<PlayerGameStats> thisSeasonGames,
                                                   List<BigDecimal> lastSeasonPoints) {
        return calculateFromPoints(pointsOf(thisSeasonGames), lastSeasonPoints);
    }

    /** The same, for callers that already have this season's points as plain values (no entities to load). */
    public static Optional<FactorResult> calculateFromPoints(List<BigDecimal> thisSeasonPoints,
                                                             List<BigDecimal> lastSeasonPoints) {
        List<BigDecimal> current = thisSeasonPoints == null ? List.of() : thisSeasonPoints;
        List<BigDecimal> last = lastSeasonPoints == null ? List.of() : lastSeasonPoints;
        if (current.isEmpty() && last.isEmpty()) {
            return Optional.empty();
        }

        double currentSum = current.stream().mapToDouble(BigDecimal::doubleValue).sum();
        double lastAverage = last.isEmpty() ? 0 : last.stream().mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
        double lastWeight = priorWeight(last.size());

        double baseline = (currentSum + lastWeight * lastAverage) / (current.size() + lastWeight);
        BigDecimal value = BigDecimal.valueOf(baseline).setScale(2, RoundingMode.HALF_UP);

        return Optional.of(new FactorResult(FACTOR_TYPE, value, WEIGHT, value,
                narrative(value, current.size(), last.size(), lastAverage, lastWeight)));
    }

    /**
     * How much evidence the baseline rests on, in games: this season's games plus
     * the weight last season counts for. The basis for a recommendation's confidence.
     */
    public static int effectiveGames(List<PlayerGameStats> thisSeasonGames, List<BigDecimal> lastSeasonPoints) {
        return effectiveGamesFromPoints(pointsOf(thisSeasonGames), lastSeasonPoints);
    }

    public static int effectiveGamesFromPoints(List<BigDecimal> thisSeasonPoints, List<BigDecimal> lastSeasonPoints) {
        int thisGames = thisSeasonPoints == null ? 0 : thisSeasonPoints.size();
        int lastGames = lastSeasonPoints == null ? 0 : lastSeasonPoints.size();
        return (int) Math.floor(thisGames + priorWeight(lastGames));
    }

    /** Last season's weight in games: the full amount for a full season, less for a short one, nothing if there was none. */
    private static double priorWeight(int lastSeasonGames) {
        return PRIOR_WEIGHT_GAMES * Math.min(lastSeasonGames, FULL_SEASON_GAMES) / FULL_SEASON_GAMES;
    }

    private static List<BigDecimal> pointsOf(List<PlayerGameStats> games) {
        if (games == null) {
            return List.of();
        }
        return games.stream()
                .map(PlayerGameStats::getFantasyPointsPpr)
                .filter(points -> points != null)
                .toList();
    }

    private static String narrative(BigDecimal value, int currentGames, int lastGames, double lastAverage, double lastWeight) {
        if (lastGames == 0) {
            return String.format("Averaging %.1f PPR points over %d %s this season",
                    value.doubleValue(), currentGames, plural(currentGames));
        }
        if (currentGames == 0) {
            return String.format("Averaging %.1f PPR points over %d %s last season (no games yet this season)",
                    value.doubleValue(), lastGames, plural(lastGames));
        }
        return String.format("Averaging %.1f PPR points over %d %s this season, with last season's %.1f average over %d %s counting as about %.1f extra games",
                value.doubleValue(), currentGames, plural(currentGames), lastAverage, lastGames, plural(lastGames), lastWeight);
    }

    private static String plural(int games) {
        return games == 1 ? "game" : "games";
    }
}
