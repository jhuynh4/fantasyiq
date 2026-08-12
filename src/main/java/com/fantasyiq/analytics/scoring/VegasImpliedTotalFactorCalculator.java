package com.fantasyiq.analytics.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Maps the team's implied point total (from betting_lines, derived from the
 * market's spread + over/under) onto a 0..1 favorability band between a low
 * and high reference total, rather than a true z-score against the league's
 * actual current-week distribution -- that would need computing the
 * population mean/stddev across every game each run. A fixed reference band
 * is a reasonable v1 simplification; revisit if backtesting shows it's off.
 */
public final class VegasImpliedTotalFactorCalculator {

    static final String FACTOR_TYPE = "VEGAS";
    private static final BigDecimal WEIGHT = BigDecimal.valueOf(20);
    private static final double LOW_REFERENCE = 17.0;
    private static final double HIGH_REFERENCE = 35.0;

    private VegasImpliedTotalFactorCalculator() {
    }

    public static Optional<FactorResult> calculate(BigDecimal impliedTeamTotal) {
        if (impliedTeamTotal == null) {
            return Optional.empty();
        }

        double total = impliedTeamTotal.doubleValue();
        double favorability = Math.max(0, Math.min(1, (total - LOW_REFERENCE) / (HIGH_REFERENCE - LOW_REFERENCE)));
        BigDecimal contribution = BigDecimal.valueOf(favorability).multiply(WEIGHT).setScale(2, RoundingMode.HALF_UP);

        String narrative = String.format("Team's Vegas implied total is %.1f points", total);

        return Optional.of(new FactorResult(FACTOR_TYPE, impliedTeamTotal, WEIGHT, contribution, narrative));
    }
}
