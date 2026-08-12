package com.fantasyiq.api.dto;

import com.fantasyiq.domain.recommendation.RecommendationFactor;

import java.math.BigDecimal;

public record RecommendationFactorResponse(String factorType, BigDecimal contribution, String narrative) {

    public static RecommendationFactorResponse from(RecommendationFactor factor) {
        return new RecommendationFactorResponse(factor.getFactorType(), factor.getContribution(), factor.getNarrative());
    }
}
