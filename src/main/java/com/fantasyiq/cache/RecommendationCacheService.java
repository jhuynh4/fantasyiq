package com.fantasyiq.cache;

import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.recommendation.RecommendationSnapshot;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cache-aside over START_SIT recommendations, one entry per season+week+position
 * (plus one for "all positions"), each already sorted by score. A client asks for
 * one position at a time, and decoding a whole week (~400 KB) to serve a quarter
 * of it was most of a cache hit's cost.
 *
 * Programmatic rather than @Cacheable/@CachePut because a refresh has to write
 * every position's entry from a single database read, which one annotated method
 * (one key per call) cannot do.
 */
@Service
public class RecommendationCacheService {

    private static final String TYPE = "START_SIT";
    private static final String ALL = "ALL";
    // Anything else is answered with an empty list without touching the cache, so a request
    // with an arbitrary position string cannot create cache entries.
    static final List<String> POSITIONS = List.of("QB", "RB", "WR", "TE");

    private final RecommendationRepository recommendationRepository;
    private final CacheManager cacheManager;

    public RecommendationCacheService(RecommendationRepository recommendationRepository, CacheManager cacheManager) {
        this.recommendationRepository = recommendationRepository;
        this.cacheManager = cacheManager;
    }

    /** Recommendations for the week, best first; {@code position} null means every position. */
    @SuppressWarnings("unchecked")
    public List<RecommendationSnapshot> getStartSit(int season, int week, String position) {
        if (position != null && !POSITIONS.contains(position)) {
            return List.of();
        }
        Cache cache = cache();
        String key = key(season, week, position);
        Cache.ValueWrapper cached = cache.get(key);
        if (cached != null) {
            return (List<RecommendationSnapshot>) cached.get();
        }
        // Cold cache (Redis restarted, or the week was never generated through this service):
        // read the database once and fill every position so the next request hits.
        return refreshStartSit(season, week).stream()
                .filter(s -> position == null || position.equals(s.position()))
                .toList();
    }

    /**
     * Called by StartSitRecommendationService right after it writes a
     * week's recommendations -- the "populated by the scoring job itself,
     * not lazily on first request" half of the cache-aside story. Returns
     * the week's full list.
     */
    public List<RecommendationSnapshot> refreshStartSit(int season, int week) {
        List<RecommendationSnapshot> all = recommendationRepository.findBySeasonAndWeekAndType(season, week, TYPE).stream()
                .map(RecommendationSnapshot::from)
                .sorted(Comparator.comparing(RecommendationSnapshot::score).reversed())
                .toList();
        Map<String, List<RecommendationSnapshot>> byPosition = all.stream()
                .collect(Collectors.groupingBy(RecommendationSnapshot::position));

        Cache cache = cache();
        cache.put(key(season, week, null), all);
        for (String position : POSITIONS) {
            // An empty list is cached too: "nobody at this position" is an answer, and leaving the
            // key out would send every request for it back to the database.
            cache.put(key(season, week, position), byPosition.getOrDefault(position, List.of()));
        }
        return all;
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(RedisCacheConfig.START_SIT_CACHE);
        if (cache == null) {
            throw new IllegalStateException("Cache " + RedisCacheConfig.START_SIT_CACHE + " is not configured");
        }
        return cache;
    }

    private static String key(int season, int week, String position) {
        return season + "-" + week + "-" + (position == null ? ALL : position);
    }
}
