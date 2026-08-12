package com.fantasyiq.api.dto;

import com.fantasyiq.domain.recommendation.Recommendation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StartSitRecommendationResponse(UUID playerId, String playerName, String position, String team,
                                              BigDecimal score, String confidence,
                                              List<RecommendationFactorResponse> factors) {

    public static StartSitRecommendationResponse from(Recommendation recommendation) {
        var player = recommendation.getPlayer();
        String teamAbbreviation = player.getCurrentTeam() != null ? player.getCurrentTeam().getAbbreviation() : null;
        List<RecommendationFactorResponse> factors = recommendation.getFactors().stream()
                .map(RecommendationFactorResponse::from)
                .toList();
        return new StartSitRecommendationResponse(player.getId(), player.getFullName(), player.getPosition(),
                teamAbbreviation, recommendation.getScore(), recommendation.getConfidence(), factors);
    }
}
