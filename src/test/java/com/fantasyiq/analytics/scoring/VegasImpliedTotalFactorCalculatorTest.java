package com.fantasyiq.analytics.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VegasImpliedTotalFactorCalculatorTest {

    @Test
    void midRangeImpliedTotalProducesProportionalContribution() {
        Optional<FactorResult> result = VegasImpliedTotalFactorCalculator.calculate(new BigDecimal("26.0"));

        assertThat(result).isPresent();
        assertThat(result.get().factorType()).isEqualTo("VEGAS");
        // (26 - 17) / (35 - 17) = 0.5 -> 0.5 * 20 = 10.00
        assertThat(result.get().contribution()).isEqualByComparingTo("10.00");
    }

    @Test
    void lowImpliedTotalIsClampedToZeroNotNegative() {
        Optional<FactorResult> result = VegasImpliedTotalFactorCalculator.calculate(new BigDecimal("10.0"));

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void highImpliedTotalIsClampedToMaxWeight() {
        Optional<FactorResult> result = VegasImpliedTotalFactorCalculator.calculate(new BigDecimal("45.0"));

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("20.00");
    }

    @Test
    void nullImpliedTotalYieldsEmptyOptional() {
        assertThat(VegasImpliedTotalFactorCalculator.calculate(null)).isEmpty();
    }
}
