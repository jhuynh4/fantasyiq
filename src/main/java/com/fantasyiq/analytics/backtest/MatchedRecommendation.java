package com.fantasyiq.analytics.backtest;

import com.fantasyiq.domain.recommendation.Recommendation;

record MatchedRecommendation(Recommendation recommendation, double actualPoints) {
}
