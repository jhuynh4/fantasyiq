package com.fantasyiq.ingestion.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ESPN's gamelog data doesn't include fantasy points -- just raw box score
 * numbers -- so this computes them using standard scoring rules. Pure
 * arithmetic on already-normalized values, not a scoring/recommendation
 * decision, so it lives here in ingestion rather than waiting for Phase 3.
 */
final class FantasyPointsCalculator {

    private FantasyPointsCalculator() {
    }

    static BigDecimal calculate(boolean ppr, Integer passingYards, Integer passingTouchdowns, Integer interceptions,
                                 Integer rushYards, Integer rushingTouchdowns, Integer receptions,
                                 Integer receivingYards, Integer receivingTouchdowns, Integer fumblesLost) {
        BigDecimal points = BigDecimal.ZERO
                .add(yards(passingYards, 25))
                .add(times(passingTouchdowns, 4))
                .add(times(interceptions, -2))
                .add(yards(rushYards, 10))
                .add(times(rushingTouchdowns, 6))
                .add(yards(receivingYards, 10))
                .add(times(receivingTouchdowns, 6))
                .add(times(fumblesLost, -2));

        if (ppr) {
            points = points.add(times(receptions, 1));
        }

        return points.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal yards(Integer value, int yardsPerPoint) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(yardsPerPoint), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal times(Integer value, int multiplier) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((long) value * multiplier);
    }
}
