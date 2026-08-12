package com.fantasyiq.analytics.scoring;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * OUT/IR is a hard override in spirit -- rather than a separate override
 * flag, the penalty is simply large enough (-1000) that no combination of
 * the other factors (each capped at a magnitude of ~25) can overcome it,
 * keeping the "sum of explainable contributions" model intact instead of
 * special-casing one factor's control flow.
 */
public final class InjuryFactorCalculator {

    static final String FACTOR_TYPE = "INJURY";

    private InjuryFactorCalculator() {
    }

    public static Optional<FactorResult> calculate(String injuryStatus) {
        if (injuryStatus == null) {
            return Optional.empty();
        }

        String status = injuryStatus.trim().toUpperCase();
        return switch (status) {
            case "OUT", "IR" -> Optional.of(new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, null,
                    BigDecimal.valueOf(-1000), "Listed as " + status + " -- effectively ruled out"));
            case "DOUBTFUL" -> Optional.of(new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, null,
                    BigDecimal.valueOf(-40), "Listed as Doubtful"));
            case "QUESTIONABLE" -> Optional.of(new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, null,
                    BigDecimal.valueOf(-10), "Listed as Questionable"));
            default -> Optional.empty();
        };
    }
}
