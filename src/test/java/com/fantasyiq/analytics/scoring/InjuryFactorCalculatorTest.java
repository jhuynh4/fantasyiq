package com.fantasyiq.analytics.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InjuryFactorCalculatorTest {

    @Test
    void outStatusProducesAnOverridingPenalty() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("OUT");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("-1000");
    }

    @Test
    void irStatusProducesAnOverridingPenalty() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("IR");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("-1000");
    }

    @Test
    void doubtfulStatusProducesAModeratePenalty() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("Doubtful");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("-40");
    }

    @Test
    void questionableStatusProducesASmallPenalty() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("questionable");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("-10");
    }

    @Test
    void healthyOrUnrecognizedStatusYieldsEmptyOptional() {
        assertThat(InjuryFactorCalculator.calculate(null)).isEmpty();
        assertThat(InjuryFactorCalculator.calculate("ACTIVE")).isEmpty();
        assertThat(InjuryFactorCalculator.calculate("PROBABLE")).isEmpty();
    }

    @Test
    void withABaselineOutAndIrAreStillTheOverridingPenalty() {
        BigDecimal baseline = new BigDecimal("20.00");

        assertThat(InjuryFactorCalculator.calculate("OUT", baseline).orElseThrow().contribution()).isEqualByComparingTo("-1000");
        assertThat(InjuryFactorCalculator.calculate("ir", baseline).orElseThrow().contribution()).isEqualByComparingTo("-1000");
    }

    @Test
    void withABaselineDoubtfulRemovesMostOfIt() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("Doubtful", new BigDecimal("20.00"));

        // 75% of 20.00
        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("-15.00");
    }

    @Test
    void withABaselineQuestionableRemovesASmallShareOfIt() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("QUESTIONABLE", new BigDecimal("20.00"));

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
    void withABaselineAPlayerWithoutPositivePointsLosesNothingToAPartialDesignation() {
        Optional<FactorResult> result = InjuryFactorCalculator.calculate("Doubtful", new BigDecimal("-1.00"));

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void withABaselineHealthyOrUnrecognizedStatusYieldsEmptyOptional() {
        BigDecimal baseline = new BigDecimal("20.00");

        assertThat(InjuryFactorCalculator.calculate(null, baseline)).isEmpty();
        assertThat(InjuryFactorCalculator.calculate("ACTIVE", baseline)).isEmpty();
        assertThat(InjuryFactorCalculator.calculate("PROBABLE", baseline)).isEmpty();
    }

    @Test
    void anOverridingPenaltyAlwaysDominatesTheOtherFactorsMaxMagnitude() {
        // Other calculators cap around a magnitude of ~25 -- the OUT penalty
        // must comfortably exceed anything they could possibly sum to.
        BigDecimal outPenalty = InjuryFactorCalculator.calculate("OUT").orElseThrow().contribution();
        assertThat(outPenalty).isLessThan(BigDecimal.valueOf(-100));
    }
}
