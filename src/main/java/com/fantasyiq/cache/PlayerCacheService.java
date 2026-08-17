package com.fantasyiq.cache;

import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.player.PlayerSnapshot;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Cache-aside over player profiles, keyed by player id. Returns a nullable
 * PlayerSnapshot rather than Optional<PlayerSnapshot> -- GenericJackson2JsonRedisSerializer
 * has no built-in support for java.util.Optional (that needs the
 * jackson-datatype-jdk8 module, not on this project's classpath) and throws
 * trying to serialize one, which surfaced as a 500 on GET /players/{id}
 * the first time this was tested against the real Redis-backed app rather
 * than just the IT suite. A null return is never cached at all --
 * RedisCacheConfig's disableCachingNullValues() means "not found" results
 * are never written to Redis, so there's no permanent negative-cache entry
 * for a 404 with no write path to ever correct it.
 */
@Service
public class PlayerCacheService {

    private final PlayerRepository playerRepository;

    public PlayerCacheService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Cacheable(cacheNames = RedisCacheConfig.PLAYER_CACHE, key = "#id")
    public PlayerSnapshot getById(UUID id) {
        return playerRepository.findById(id).map(PlayerSnapshot::from).orElse(null);
    }

    /**
     * Called by PlayerIngestionService right after a player is upserted --
     * the "populated by the ingestion job itself" half of the cache-aside
     * story, same as RecommendationCacheService.refreshStartSit.
     */
    @CachePut(cacheNames = RedisCacheConfig.PLAYER_CACHE, key = "#player.getId()")
    public PlayerSnapshot refresh(Player player) {
        return PlayerSnapshot.from(player);
    }
}
