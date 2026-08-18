package com.fantasyiq.domain.recommendation;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Flattened, Redis-cacheable view of a Recommendation + its factors --
 * exists so the cache layer (com.fantasyiq.cache) and the write side
 * (analytics.startsit) can share one plain value instead of serializing the
 * JPA entity graph directly, and so the read side (api.dto) doesn't need
 * either layer to depend on api.dto's response records (analytics/ingestion
 * must not depend on api).
 */
public record RecommendationSnapshot(UUID playerId, String playerName, String position, String team,
                                      BigDecimal score, String confidence,
                                      List<FactorSnapshot> factors) implements Serializable {

    public record FactorSnapshot(String factorType, BigDecimal factorValue, BigDecimal factorWeight,
                                  BigDecimal contribution, String narrative) implements Serializable {
    }

    public static RecommendationSnapshot from(Recommendation recommendation) {
        var player = recommendation.getPlayer();
        String teamAbbreviation = player.getCurrentTeam() != null ? player.getCurrentTeam().getAbbreviation() : null;
        List<FactorSnapshot> factors = recommendation.getFactors().stream()
                .map(f -> new FactorSnapshot(f.getFactorType(), f.getFactorValue(), f.getFactorWeight(),
                        f.getContribution(), f.getNarrative()))
                .toList();
        return new RecommendationSnapshot(player.getId(), player.getFullName(), player.getPosition(),
                teamAbbreviation, recommendation.getScore(), recommendation.getConfidence(), factors);
    }
}
