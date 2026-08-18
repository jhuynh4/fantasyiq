package com.fantasyiq.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Cache-aside, but populated primarily by the ingestion/scoring jobs
 * themselves right after they write (see RecommendationCacheService.refreshStartSit
 * and PlayerCacheService.refresh), not lazily on first request -- a request
 * arriving before the relevant job has ever run still falls back to a real
 * DB read via the @Cacheable methods, it just won't have been pre-warmed.
 * TTLs below are a staleness ceiling for that fallback path, not the primary
 * invalidation mechanism.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String START_SIT_CACHE = "startSitRecommendations";
    public static final String PLAYER_CACHE = "playerDetails";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults.entryTtl(Duration.ofHours(6)))
                // Recommendations for a given season/week are immutable once
                // that week's /generate run finishes -- no new games get
                // added to a past week -- so a long ceiling is safe.
                .withCacheConfiguration(START_SIT_CACHE, defaults.entryTtl(Duration.ofDays(2)))
                // Roster moves happen; bounded by daily player ingestion anyway.
                .withCacheConfiguration(PLAYER_CACHE, defaults.entryTtl(Duration.ofHours(24)))
                .build();
    }

    /**
     * GenericJackson2JsonRedisSerializer's no-arg constructor builds its own
     * internal ObjectMapper that does NOT register jackson-datatype-jsr310 --
     * fine for RecommendationSnapshot (no java.time fields) but threw
     * serializing PlayerSnapshot.birthDate (a LocalDate) the first time this
     * was tested against a real Redis instance rather than just mocked/unit
     * coverage. findAndRegisterModules() picks up jsr310 (already a project
     * dependency, used elsewhere for API JSON) via its own SPI registration.
     * The polymorphic-typing activation below matches what
     * GenericJackson2JsonRedisSerializer's own default mapper does
     * internally (needed so it can deserialize back into the right concrete
     * type from the stored @class hint) -- lost if a caller supplies a plain
     * ObjectMapper without it. DefaultTyping.EVERYTHING, not NON_FINAL --
     * NON_FINAL skips embedding the @class id for `final` types, and every
     * cached value here (PlayerSnapshot, RecommendationSnapshot) is a Java
     * `record`, which is implicitly final. Using NON_FINAL let a write
     * silently omit the type id, then threw InvalidTypeIdException on the
     * very next read trying to resolve a concrete type with nothing to
     * resolve it from -- caught testing against a real Redis instance
     * rather than mocked/unit coverage, same as the LocalDate issue above.
     */
    private static ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
