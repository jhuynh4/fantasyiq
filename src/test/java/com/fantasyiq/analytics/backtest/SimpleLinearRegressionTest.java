package com.fantasyiq.analytics.backtest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SimpleLinearRegressionTest {

    @Test
    void fitsExactlyOnPerfectlyLinearData() {
        // y = 2x + 3
        List<Double> xs = List.of(1.0, 2.0, 3.0, 4.0);
        List<Double> ys = List.of(5.0, 7.0, 9.0, 11.0);

        SimpleLinearRegression.Fit fit = SimpleLinearRegression.of(xs, ys).orElseThrow();

        assertThat(fit.slope()).isCloseTo(2.0, offset(0.0001));
        assertThat(fit.intercept()).isCloseTo(3.0, offset(0.0001));
    }

    @Test
    void slopeCloseToOneMeansCurrentScaleAlreadyWellCalibrated() {
        // actual == contribution exactly -- the "already well-tuned" case
        List<Double> contributions = List.of(10.0, 15.0, 20.0, 25.0);
        List<Double> actuals = List.of(10.0, 15.0, 20.0, 25.0);

        SimpleLinearRegression.Fit fit = SimpleLinearRegression.of(contributions, actuals).orElseThrow();

        assertThat(fit.slope()).isCloseTo(1.0, offset(0.0001));
    }

    @Test
    void negativeSlopeMeansInverseRelationship() {
        List<Double> xs = List.of(1.0, 2.0, 3.0, 4.0);
        List<Double> ys = List.of(40.0, 30.0, 20.0, 10.0);

        SimpleLinearRegression.Fit fit = SimpleLinearRegression.of(xs, ys).orElseThrow();

        assertThat(fit.slope()).isCloseTo(-10.0, offset(0.0001));
    }

    @Test
    void tooFewDataPointsYieldsEmpty() {
        assertThat(SimpleLinearRegression.of(List.of(), List.of())).isEmpty();
        assertThat(SimpleLinearRegression.of(List.of(1.0), List.of(2.0))).isEmpty();
    }

    @Test
    void mismatchedListSizesYieldsEmpty() {
        assertThat(SimpleLinearRegression.of(List.of(1.0, 2.0), List.of(1.0))).isEmpty();
    }

    @Test
    void noVarianceInXYieldsEmpty() {
        List<Double> xs = List.of(5.0, 5.0, 5.0);
        List<Double> ys = List.of(1.0, 2.0, 3.0);

        assertThat(SimpleLinearRegression.of(xs, ys)).isEmpty();
    }

    @Test
    void nullInputsYieldEmpty() {
        assertThat(SimpleLinearRegression.of(null, List.of(1.0, 2.0))).isEmpty();
        assertThat(SimpleLinearRegression.of(List.of(1.0, 2.0), null)).isEmpty();
    }
}
