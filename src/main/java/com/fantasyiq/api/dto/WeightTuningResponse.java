package com.fantasyiq.api.dto;

import com.fantasyiq.analytics.backtest.WeightTuningResult;

import java.util.List;

public record WeightTuningResponse(int season, int totalMatchedRecommendations,
                                    List<FactorTuningSuggestionResponse> suggestions) {

    public static WeightTuningResponse from(WeightTuningResult result) {
        List<FactorTuningSuggestionResponse> suggestions = result.suggestions().stream()
                .map(FactorTuningSuggestionResponse::from)
                .toList();
        return new WeightTuningResponse(result.season(), result.totalMatchedRecommendations(), suggestions);
    }
}
