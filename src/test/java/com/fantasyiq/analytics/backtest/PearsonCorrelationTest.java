package com.fantasyiq.analytics.backtest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class PearsonCorrelationTest {

    @Test
    void perfectlyCorrelatedDataYieldsOne() {
        List<Double> xs = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        List<Double> ys = List.of(10.0, 20.0, 30.0, 40.0, 50.0);

        assertThat(PearsonCorrelation.of(xs, ys)).isCloseTo(1.0, offset(0.0001));
    }

    @Test
    void perfectlyInverselyCorrelatedDataYieldsNegativeOne() {
        List<Double> xs = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        List<Double> ys = List.of(50.0, 40.0, 30.0, 20.0, 10.0);

        assertThat(PearsonCorrelation.of(xs, ys)).isCloseTo(-1.0, offset(0.0001));
    }

    @Test
    void unrelatedDataYieldsSomethingCloseToZero() {
        List<Double> xs = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        List<Double> ys = List.of(5.0, 1.0, 4.0, 2.0, 3.0, 6.0);

        Double result = PearsonCorrelation.of(xs, ys);

        assertThat(result).isNotNull();
        assertThat(Math.abs(result)).isLessThan(0.5);
    }

    @Test
    void tooFewDataPointsYieldsNull() {
        assertThat(PearsonCorrelation.of(List.of(), List.of())).isNull();
        assertThat(PearsonCorrelation.of(List.of(1.0), List.of(2.0))).isNull();
    }

    @Test
    void mismatchedListSizesYieldsNull() {
        assertThat(PearsonCorrelation.of(List.of(1.0, 2.0), List.of(1.0))).isNull();
    }

    @Test
    void noVarianceInEitherSeriesYieldsNull() {
        // Every x is identical -- there's no spread to correlate against.
        List<Double> xs = List.of(5.0, 5.0, 5.0);
        List<Double> ys = List.of(1.0, 2.0, 3.0);

        assertThat(PearsonCorrelation.of(xs, ys)).isNull();
    }

    @Test
    void nullInputsYieldNull() {
        assertThat(PearsonCorrelation.of(null, List.of(1.0, 2.0))).isNull();
        assertThat(PearsonCorrelation.of(List.of(1.0, 2.0), null)).isNull();
    }
}
