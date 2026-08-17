package com.fantasyiq.api.dto;

import com.fantasyiq.domain.recommendation.RecommendationSnapshot;

import java.math.BigDecimal;

public record RecommendationFactorResponse(String factorType, BigDecimal contribution, String narrative) {

    public static RecommendationFactorResponse from(RecommendationSnapshot.FactorSnapshot factor) {
        return new RecommendationFactorResponse(factor.factorType(), factor.contribution(), factor.narrative());
    }
}
