package com.fantasyiq.api.dto;

import com.fantasyiq.analytics.scoring.FactorResult;
import com.fantasyiq.domain.recommendation.RecommendationSnapshot;

import java.math.BigDecimal;

public record FactorResponse(String factorType, BigDecimal contribution, String narrative) {

    public static FactorResponse from(RecommendationSnapshot.FactorSnapshot factor) {
        return new FactorResponse(factor.factorType(), factor.contribution(), factor.narrative());
    }

    public static FactorResponse from(FactorResult factor) {
        return new FactorResponse(factor.factorType(), factor.contribution(), factor.narrative());
    }
}
