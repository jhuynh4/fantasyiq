package com.fantasyiq.api.dto;

import com.fantasyiq.analytics.backtest.BacktestResult;

import java.util.Map;

public record BacktestResponse(int season, int weeksEvaluated, int recommendationsEvaluated,
                                int excludedDueToInjuryOverride, int matchedWithActualStats,
                                Double overallCorrelation, Map<String, Double> correlationByPosition) {

    public static BacktestResponse from(BacktestResult result) {
        return new BacktestResponse(result.season(), result.weeksEvaluated(), result.recommendationsEvaluated(),
                result.excludedDueToInjuryOverride(), result.matchedWithActualStats(),
                result.overallCorrelation(), result.correlationByPosition());
    }
}
