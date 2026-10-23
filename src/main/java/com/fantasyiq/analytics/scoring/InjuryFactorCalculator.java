package com.fantasyiq.analytics.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Two entry points, because the two features ask different questions.
 *
 * Start/sit (this week): OUT/IR is a hard override -- rather than a separate
 * override flag, the penalty is simply large enough (-1000) that no
 * combination of the other factors can overcome it, keeping the "sum of
 * explainable contributions" model intact instead of special-casing one
 * factor's control flow. Doubtful and Questionable remove a share of the
 * player's baseline.
 *
 * Trade value (rest of the season): a status is a short-term fact, so a flat
 * -1000 would value a one-week absence as a lost season. Each status instead
 * removes the share of the remaining season it is expected to cost.
 *
 * Both sets of shares are judgement calls: injury status in this database is
 * only the player's current one, so there is no history to fit them against.
 */
public final class InjuryFactorCalculator {

    static final String FACTOR_TYPE = "INJURY";
    private static final BigDecimal DOUBTFUL_SHARE = new BigDecimal("0.75");
    private static final BigDecimal QUESTIONABLE_SHARE = new BigDecimal("0.15");
    private static final BigDecimal IR_SHARE = new BigDecimal("0.50");
    private static final BigDecimal OUT_SHARE = new BigDecimal("0.15");
    private static final BigDecimal DOUBTFUL_SHARE_SEASON = new BigDecimal("0.10");
    private static final BigDecimal QUESTIONABLE_SHARE_SEASON = new BigDecimal("0.03");

    private InjuryFactorCalculator() {
    }

    /**
     * Start/sit: OUT/IR is the -1000 override; Doubtful and Questionable
     * remove a share of the baseline.
     */
    public static Optional<FactorResult> calculate(String injuryStatus, BigDecimal baseline) {
        if (injuryStatus == null) {
            return Optional.empty();
        }

        String status = normalize(injuryStatus);
        BigDecimal base = baseline.max(BigDecimal.ZERO);
        return switch (status) {
            case "OUT", "IR", "SUSPENSION" -> Optional.of(ruledOut(status));
            case "DOUBTFUL" -> Optional.of(new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, DOUBTFUL_SHARE,
                    base.multiply(DOUBTFUL_SHARE).negate().setScale(2, RoundingMode.HALF_UP),
                    "Listed as Doubtful -- unlikely to play"));
            case "QUESTIONABLE" -> Optional.of(new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, QUESTIONABLE_SHARE,
                    base.multiply(QUESTIONABLE_SHARE).negate().setScale(2, RoundingMode.HALF_UP),
                    "Listed as Questionable"));
            default -> Optional.empty();
        };
    }

    /**
     * Trade value: the share of the rest of the season each status is expected
     * to cost, roughly games missed over games left (~12). IR is at least four
     * weeks and usually longer; OUT is typically one or two games.
     */
    public static Optional<FactorResult> calculateForRestOfSeason(String injuryStatus, BigDecimal baseline) {
        if (injuryStatus == null) {
            return Optional.empty();
        }

        String status = normalize(injuryStatus);
        return switch (status) {
            case "IR" -> Optional.of(restOfSeason(baseline, IR_SHARE, "Listed as IR -- assumed to miss about half the rest of the season"));
            case "OUT", "SUSPENSION" -> Optional.of(restOfSeason(baseline, OUT_SHARE,
                    "Listed as " + status + " -- assumed to miss a game or two"));
            case "DOUBTFUL" -> Optional.of(restOfSeason(baseline, DOUBTFUL_SHARE_SEASON, "Listed as Doubtful -- may miss a game"));
            case "QUESTIONABLE" -> Optional.of(restOfSeason(baseline, QUESTIONABLE_SHARE_SEASON, "Listed as Questionable -- little effect over a season"));
            default -> Optional.empty();
        };
    }

    private static FactorResult restOfSeason(BigDecimal baseline, BigDecimal share, String narrative) {
        return new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, share,
                baseline.max(BigDecimal.ZERO).multiply(share).negate().setScale(2, RoundingMode.HALF_UP), narrative);
    }

    /**
     * ESPN's status for injured reserve is the literal "INJURED RESERVE" (found
     * in real data: every one of those players was being read as healthy when
     * only "IR" was recognized). Its other statuses seen so far are OUT,
     * QUESTIONABLE and SUSPENSION.
     */
    private static String normalize(String injuryStatus) {
        String status = injuryStatus.trim().toUpperCase();
        return status.equals("INJURED RESERVE") ? "IR" : status;
    }

    private static FactorResult ruledOut(String status) {
        return new FactorResult(FACTOR_TYPE, BigDecimal.ZERO, null,
                BigDecimal.valueOf(-1000), "Listed as " + status + " -- effectively ruled out");
    }
}
