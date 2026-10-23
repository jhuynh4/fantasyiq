package com.fantasyiq.analytics.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * OUT/IR is a hard override in spirit -- rather than a separate override
 * flag, the penalty is simply large enough (-1000) that no combination of
 * the other factors can overcome it, keeping the "sum of explainable
 * contributions" model intact instead of special-casing one factor's control
 * flow.
 *
 * Two entry points because two features score injuries differently. Start/sit
 * (v2) scores in expected points, so Doubtful and Questionable remove a share
 * of the player's baseline. The trade analyzer still scores on the v1 scale
 * and keeps the flat penalties.
 */
public final class InjuryFactorCalculator {

    static final String FACTOR_TYPE = "INJURY";
    private static final BigDecimal DOUBTFUL_SHARE = new BigDecimal("0.75");
    private static final BigDecimal QUESTIONABLE_SHARE = new BigDecimal("0.15");

    private InjuryFactorCalculator() {
    }

    /** Flat penalties, on the v1 scale (still used by the trade analyzer). */
    public static Optional<FactorResult> calculate(String injuryStatus) {
        if (injuryStatus == null) {
            return Optional.empty();
        }

        String status = injuryStatus.trim().toUpperCase();
        return switch (status) {
            case "OUT", "IR" -> Optional.of(ruledOut(status));
            case "DOUBTFUL" -> Optional.of(new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, null,
                    BigDecimal.valueOf(-40), "Listed as Doubtful"));
            case "QUESTIONABLE" -> Optional.of(new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, null,
                    BigDecimal.valueOf(-10), "Listed as Questionable"));
            default -> Optional.empty();
        };
    }

    /**
     * Start/sit (v2): OUT/IR is still the -1000 override; Doubtful and
     * Questionable remove a share of the baseline. The shares are judgement
     * calls -- injury status in this database is only the player's current one,
     * so there is no history to fit them against.
     */
    public static Optional<FactorResult> calculate(String injuryStatus, BigDecimal baseline) {
        if (injuryStatus == null) {
            return Optional.empty();
        }

        String status = injuryStatus.trim().toUpperCase();
        BigDecimal base = baseline.max(BigDecimal.ZERO);
        return switch (status) {
            case "OUT", "IR" -> Optional.of(ruledOut(status));
            case "DOUBTFUL" -> Optional.of(new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, DOUBTFUL_SHARE,
                    base.multiply(DOUBTFUL_SHARE).negate().setScale(2, RoundingMode.HALF_UP),
                    "Listed as Doubtful -- unlikely to play"));
            case "QUESTIONABLE" -> Optional.of(new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, QUESTIONABLE_SHARE,
                    base.multiply(QUESTIONABLE_SHARE).negate().setScale(2, RoundingMode.HALF_UP),
                    "Listed as Questionable"));
            default -> Optional.empty();
        };
    }

    private static FactorResult ruledOut(String status) {
        return new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, null,
                BigDecimal.valueOf(-1000), "Listed as " + status + " -- effectively ruled out");
    }
}
