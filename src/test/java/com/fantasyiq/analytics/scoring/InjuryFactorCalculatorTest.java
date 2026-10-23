package com.fantasyiq.analytics.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InjuryFactorCalculatorTest {

    private static final BigDecimal BASELINE = new BigDecimal("20.00");

    @Test
    void outAndIrAreTheOverridingPenaltyForThisWeek() {
        assertThat(InjuryFactorCalculator.calculate("OUT", BASELINE).orElseThrow().contribution()).isEqualByComparingTo("-1000");
        assertThat(InjuryFactorCalculator.calculate("ir", BASELINE).orElseThrow().contribution()).isEqualByComparingTo("-1000");
    }

    @Test
    void doubtfulRemovesMostOfTheBaseline() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("Doubtful", BASELINE);

        // 75% of 20.00
        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("-15.00");
    }

    @Test
    void questionableRemovesASmallShareOfTheBaseline() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("QUESTIONABLE", BASELINE);

        // 15% of 20.00
        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("-3.00");
    }

    @Test
    void theShareScalesWithTheBaselineSoABackupIsNotWipedOutByAStarsPenalty() {
        BigDecimal star = InjuryFactorCalculator.calculate("QUESTIONABLE", new BigDecimal("24.00")).orElseThrow().contribution();
        BigDecimal backup = InjuryFactorCalculator.calculate("QUESTIONABLE", new BigDecimal("3.00")).orElseThrow().contribution();

        assertThat(star).isEqualByComparingTo("-3.60");
        assertThat(backup).isEqualByComparingTo("-0.45");
    }

    @Test
    void aPlayerWithoutPositivePointsLosesNothingToAPartialDesignation() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("Doubtful", new BigDecimal("-1.00"));

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void healthyOrUnrecognizedStatusYieldsEmptyOptional() {
        assertThat(InjuryFactorCalculator.calculate(null, BASELINE)).isEmpty();
        assertThat(InjuryFactorCalculator.calculate("ACTIVE", BASELINE)).isEmpty();
        assertThat(InjuryFactorCalculator.calculate("PROBABLE", BASELINE)).isEmpty();
    }

    @Test
    void anOverridingPenaltyAlwaysDominatesAnyOtherFactorsPossibleSwing() {
        BigDecimal outPenalty = InjuryFactorCalculator.calculate("OUT", BASELINE).orElseThrow().contribution();

        assertThat(outPenalty).isLessThan(BigDecimal.valueOf(-100));
    }

    @Test
    void overTheRestOfTheSeasonIrCostsAboutHalfTheValue() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculateForRestOfSeason("IR", BASELINE);

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("-10.00");
        assertThat(result.get().narrative()).contains("IR");
    }

    @Test
    void overTheRestOfTheSeasonOutIsAShortAbsenceNotALostSeason() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculateForRestOfSeason("OUT", BASELINE);

        // 15% of 20.00 -- a flat -1000 would have valued this as a lost season.
        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("-3.00");
    }

    @Test
    void overTheRestOfTheSeasonDoubtfulAndQuestionableCostLess() {
        assertThat(InjuryFactorCalculator.calculateForRestOfSeason("Doubtful", BASELINE).orElseThrow().contribution())
                .isEqualByComparingTo("-2.00");
        assertThat(InjuryFactorCalculator.calculateForRestOfSeason("questionable", BASELINE).orElseThrow().contribution())
                .isEqualByComparingTo("-0.60");
    }

    @Test
    void overTheRestOfTheSeasonAHealthyOrUnknownStatusYieldsEmptyOptional() {
        assertThat(InjuryFactorCalculator.calculateForRestOfSeason(null, BASELINE)).isEmpty();
        assertThat(InjuryFactorCalculator.calculateForRestOfSeason("ACTIVE", BASELINE)).isEmpty();
    }

    @Test
    void overTheRestOfTheSeasonEvenIrLeavesTheBaselineWithPositiveValue() {
        // Even the heaviest designation only removes a share of the baseline.
        BigDecimal ir = InjuryFactorCalculator.calculateForRestOfSeason("IR", BASELINE).orElseThrow().contribution();

        assertThat(BASELINE.add(ir)).isPositive();
    }

    @Test
    void espnsInjuredReserveWordingIsTreatedAsIr() {
        assertThat(InjuryFactorCalculator.calculate("INJURED RESERVE", BASELINE).orElseThrow().contribution())
                .isEqualByComparingTo("-1000");
        assertThat(InjuryFactorCalculator.calculateForRestOfSeason("Injured Reserve", BASELINE).orElseThrow().contribution())
                .isEqualByComparingTo("-10.00");
    }

    @Test
    void aSuspendedPlayerIsOutThisWeekAndMissesAGameOrTwoOverTheSeason() {
        assertThat(InjuryFactorCalculator.calculate("SUSPENSION", BASELINE).orElseThrow().contribution())
                .isEqualByComparingTo("-1000");
        assertThat(InjuryFactorCalculator.calculateForRestOfSeason("SUSPENSION", BASELINE).orElseThrow().contribution())
                .isEqualByComparingTo("-3.00");
    }
}
