package com.fantasyiq.cache;

import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.recommendation.RecommendationSnapshot;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cache-aside over START_SIT recommendations, keyed by season+week (never by
 * position -- position filtering happens after a cache read, in the
 * controller, so there's one cache entry per week rather than one per
 * week*position combination).
 */
@Service
public class RecommendationCacheService {

    private static final String TYPE = "START_SIT";

    private final RecommendationRepository recommendationRepository;

    public RecommendationCacheService(RecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    @Cacheable(cacheNames = RedisCacheConfig.START_SIT_CACHE, key = "#season + '-' + #week")
    public List<RecommendationSnapshot> getStartSit(int season, int week) {
        return loadFromDb(season, week);
    }

    /**
     * Called by StartSitRecommendationService right after it writes a
     * week's recommendations -- the "populated by the scoring job itself,
     * not lazily on first request" half of the cache-aside story.
     */
    @CachePut(cacheNames = RedisCacheConfig.START_SIT_CACHE, key = "#season + '-' + #week")
    public List<RecommendationSnapshot> refreshStartSit(int season, int week) {
        return loadFromDb(season, week);
    }

    private List<RecommendationSnapshot> loadFromDb(int season, int week) {
        return recommendationRepository.findBySeasonAndWeekAndType(season, week, TYPE).stream()
                .map(RecommendationSnapshot::from)
                .toList();
    }
}
