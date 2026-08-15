package com.fantasyiq.api.dto;

import com.fantasyiq.analytics.backtest.FactorTuningSuggestion;

public record FactorTuningSuggestionResponse(String factorType, int dataPoints, Double correlationWithActual,
                                              Double suggestedScaleMultiplier) {

    public static FactorTuningSuggestionResponse from(FactorTuningSuggestion suggestion) {
        return new FactorTuningSuggestionResponse(suggestion.factorType(), suggestion.dataPoints(),
                suggestion.correlationWithActual(), suggestion.suggestedScaleMultiplier());
    }
}
