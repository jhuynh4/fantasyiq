package com.fantasyiq.analytics.backtest;

import java.util.List;

/**
 * Standard Pearson product-moment correlation coefficient, hand-rolled
 * rather than pulling in a stats library for one formula. Returns null
 * (not NaN/0) when there isn't enough data or no variance to correlate
 * against -- "we can't tell" is a different answer than "no correlation".
 */
final class PearsonCorrelation {

    private PearsonCorrelation() {
    }

    static Double of(List<Double> xs, List<Double> ys) {
        if (xs == null || ys == null || xs.size() != ys.size() || xs.size() < 2) {
            return null;
        }

        double meanX = xs.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double meanY = ys.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

        double covariance = 0;
        double varianceX = 0;
        double varianceY = 0;
        for (int i = 0; i < xs.size(); i++) {
            double dx = xs.get(i) - meanX;
            double dy = ys.get(i) - meanY;
            covariance += dx * dy;
            varianceX += dx * dx;
            varianceY += dy * dy;
        }

        if (varianceX == 0 || varianceY == 0) {
            return null;
        }

        return covariance / Math.sqrt(varianceX * varianceY);
    }
}
