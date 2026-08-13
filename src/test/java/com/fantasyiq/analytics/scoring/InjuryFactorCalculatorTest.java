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
    void anOverridingPenaltyAlwaysDominatesTheOtherFactorsMaxMagnitude() {
        // Other calculators cap around a magnitude of ~25 -- the OUT penalty
        // must comfortably exceed anything they could possibly sum to.
        BigDecimal outPenalty = InjuryFactorCalculator.calculate("OUT").orElseThrow().contribution();
        assertThat(outPenalty).isLessThan(BigDecimal.valueOf(-100));
    }
}
