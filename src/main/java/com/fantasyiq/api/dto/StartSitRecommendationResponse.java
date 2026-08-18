package com.fantasyiq.api.dto;

import com.fantasyiq.domain.recommendation.RecommendationSnapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StartSitRecommendationResponse(UUID playerId, String playerName, String position, String team,
                                              BigDecimal score, String confidence,
                                              List<RecommendationFactorResponse> factors) {

    public static StartSitRecommendationResponse from(RecommendationSnapshot snapshot) {
        List<RecommendationFactorResponse> factors = snapshot.factors().stream()
                .map(RecommendationFactorResponse::from)
                .toList();
        return new StartSitRecommendationResponse(snapshot.playerId(), snapshot.playerName(), snapshot.position(),
                snapshot.team(), snapshot.score(), snapshot.confidence(), factors);
    }
}
