package com.fantasyiq.analytics.backtest;

/**
 * suggestedScaleMultiplier is the regression slope of actual fantasy points
 * against this factor's *current* contribution -- since both are already in
 * "points" units, the slope directly answers "multiply this factor's
 * existing weight/scale constant by this much to better match reality".
 * ~1.0 means already well-calibrated; higher means under-weighted; lower
 * (or negative) means over-weighted or not actually predictive as built.
 * Null (both fields) when there wasn't enough matched data with this
 * factor present to fit anything -- "we can't tell", not "no effect".
 */
public record FactorTuningSuggestion(String factorType, int dataPoints, Double correlationWithActual,
                                      Double suggestedScaleMultiplier) {
}
