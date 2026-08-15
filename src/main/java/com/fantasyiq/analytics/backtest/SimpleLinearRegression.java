package com.fantasyiq.analytics.backtest;

import java.util.List;
import java.util.Optional;

/**
 * Ordinary least squares for a single predictor (y = slope*x + intercept),
 * hand-rolled like PearsonCorrelation rather than pulling in a stats
 * library. Used to fit "actual fantasy points" against one factor's
 * current contribution at a time -- deliberately univariate rather than a
 * full multi-variable regression, consistent with this project's
 * explainability-first choice of a weighted-linear model over anything
 * harder to inspect: one slope per factor is directly interpretable as
 * "how much to rescale this factor's existing weight", no need to reason
 * about interactions between factors.
 */
final class SimpleLinearRegression {

    private SimpleLinearRegression() {
    }

    record Fit(double slope, double intercept) {
    }

    static Optional<Fit> of(List<Double> xs, List<Double> ys) {
        if (xs == null || ys == null || xs.size() != ys.size() || xs.size() < 2) {
            return Optional.empty();
        }

        double meanX = xs.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double meanY = ys.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

        double covariance = 0;
        double varianceX = 0;
        for (int i = 0; i < xs.size(); i++) {
            double dx = xs.get(i) - meanX;
            covariance += dx * (ys.get(i) - meanY);
            varianceX += dx * dx;
        }

        if (varianceX == 0) {
            return Optional.empty();
        }

        double slope = covariance / varianceX;
        double intercept = meanY - slope * meanX;
        return Optional.of(new Fit(slope, intercept));
    }
}
