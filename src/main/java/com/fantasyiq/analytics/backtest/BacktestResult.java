package com.fantasyiq.analytics.backtest;

import java.util.Map;

/**
 * overallCorrelation/correlationByPosition are null when there wasn't
 * enough matched data (or no variance) to compute a coefficient -- see
 * PearsonCorrelation. excludedDueToInjuryOverride is reported separately
 * from matchedWithActualStats for transparency: InjuryFactorCalculator
 * uses the player's *current* status (not season/week-scoped, see
 * CLAUDE.md), which is meaningless for a past week, so an OUT/IR-dominated
 * score compared against real box-score points would just be synthetic
 * noise, not a real test of the other four factors' predictive value.
 */
public record BacktestResult(int season, int weeksEvaluated, int recommendationsEvaluated,
                              int excludedDueToInjuryOverride, int matchedWithActualStats,
                              Double overallCorrelation, Map<String, Double> correlationByPosition) {
}
