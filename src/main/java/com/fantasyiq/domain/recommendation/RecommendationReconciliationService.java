package com.fantasyiq.domain.recommendation;

import com.fantasyiq.domain.player.Player;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Resolves/creates the Recommendation shell only -- the caller (analytics
 * layer, which owns the FactorResult -> RecommendationFactor translation)
 * builds the factor list afterward using the returned managed entity and
 * calls Recommendation.replaceFactors(...) directly, since RecommendationFactor
 * needs a Recommendation reference to construct and this service can't know
 * the factor breakdown itself without depending on the analytics layer.
 */
@Service
public class RecommendationReconciliationService {

    private final RecommendationRepository recommendationRepository;

    public RecommendationReconciliationService(RecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    @Transactional
    public Recommendation resolveOrCreate(Player player, Integer season, Integer week, String type,
                                           BigDecimal score, String confidence, String scoringVersion) {
        Optional<Recommendation> existing = recommendationRepository
                .findByPlayerAndSeasonAndWeekAndType(player, season, week, type);

        if (existing.isPresent()) {
            Recommendation recommendation = existing.get();
            recommendation.updateFrom(score, confidence, scoringVersion);
            return recommendation;
        }

        return recommendationRepository.save(
                new Recommendation(player, season, week, type, score, confidence, scoringVersion));
    }
}
