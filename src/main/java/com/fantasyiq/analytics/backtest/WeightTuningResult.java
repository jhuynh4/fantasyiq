package com.fantasyiq.analytics.backtest;

import java.util.List;

public record WeightTuningResult(int season, int totalMatchedRecommendations, List<FactorTuningSuggestion> suggestions) {
}
