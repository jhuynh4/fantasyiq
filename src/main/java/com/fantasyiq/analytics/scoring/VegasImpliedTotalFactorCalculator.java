package com.fantasyiq.analytics.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * A percentage adjustment of the player's baseline: a team the market expects
 * to score more than a typical NFL team gets its players nudged up, and the
 * reverse. The adjustment is the team's implied total relative to a typical
 * team total, applied at a quarter of its face value (DAMPING).
 *
 * Unlike MATCHUP, this has never been backtested: betting_lines holds no
 * history before the current season. DAMPING is therefore a deliberately
 * conservative first-principles choice (the swing is at most about +-10% of
 * the baseline), not a fitted one -- revisit once a season of lines exists.
 */
public final class VegasImpliedTotalFactorCalculator {

    static final String FACTOR_TYPE = "VEGAS";
    static final BigDecimal DAMPING = new BigDecimal("0.25");
    /** Roughly the league's recent average points per team per game. */
    static final double TYPICAL_TEAM_TOTAL = 22.5;

    private VegasImpliedTotalFactorCalculator() {
    }

    public static Optional<FactorResult> calculate(BigDecimal impliedTeamTotal, BigDecimal baseline) {
        if (impliedTeamTotal == null) {
            return Optional.empty();
        }

        double total = impliedTeamTotal.doubleValue();
        double relative = total / TYPICAL_TEAM_TOTAL - 1;
        BigDecimal contribution = baseline.max(BigDecimal.ZERO)
                .multiply(DAMPING)
                .multiply(BigDecimal.valueOf(relative))
                .setScale(2, RoundingMode.HALF_UP);

        String narrative = String.format("Team's Vegas implied total is %.1f points, %.0f%% %s a typical team's %.1f",
                total, Math.abs(relative) * 100, relative >= 0 ? "above" : "below", TYPICAL_TEAM_TOTAL);

        return Optional.of(new FactorResult(FACTOR_TYPE, impliedTeamTotal, DAMPING, contribution, narrative));
    }
}
