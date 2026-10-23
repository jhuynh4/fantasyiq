package com.fantasyiq.analytics.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VegasImpliedTotalFactorCalculatorTest {

    private static final BigDecimal BASELINE = new BigDecimal("16.00");

    @Test
    void aHighScoringTeamRaisesTheBaseline() {
        Optional<FactorResult> result = VegasImpliedTotalFactorCalculator.calculate(new BigDecimal("27.0"), BASELINE);

        assertThat(result).isPresent();
        assertThat(result.get().factorType()).isEqualTo("VEGAS");
        // 27.0 / 22.5 - 1 = +20%; 16.00 * 0.25 * 0.20 = 0.80
        assertThat(result.get().contribution()).isEqualByComparingTo("0.80");
        assertThat(result.get().narrative()).contains("27.0").contains("20% above");
    }

    @Test
    void aLowScoringTeamLowersTheBaseline() {
        Optional<FactorResult> result = VegasImpliedTotalFactorCalculator.calculate(new BigDecimal("18.0"), BASELINE);

        // 18.0 / 22.5 - 1 = -20%; 16.00 * 0.25 * -0.20 = -0.80
        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("-0.80");
        assertThat(result.get().narrative()).contains("20% below");
    }

    @Test
    void aTypicalTotalChangesNothing() {
        Optional<FactorResult> result = VegasImpliedTotalFactorCalculator.calculate(new BigDecimal("22.5"), BASELINE);

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void theSwingStaysSmallEvenForAnExtremeTotal() {
        // 40 points implied is far beyond any real line; the nudge is still under a quarter of the baseline.
        Optional<FactorResult> result = VegasImpliedTotalFactorCalculator.calculate(new BigDecimal("40.0"), BASELINE);

        assertThat(result.orElseThrow().contribution()).isLessThan(BASELINE.multiply(new BigDecimal("0.25")));
    }

    @Test
    void aPlayerWithNoPositiveBaselineIsNotAdjusted() {
        Optional<FactorResult> result = VegasImpliedTotalFactorCalculator.calculate(new BigDecimal("30.0"), BigDecimal.ZERO);

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void nullImpliedTotalYieldsEmptyOptional() {
        assertThat(VegasImpliedTotalFactorCalculator.calculate(null, BASELINE)).isEmpty();
    }
}
