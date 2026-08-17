package com.fantasyiq;

import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.PlayerExternalIdRepository;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.stats.BettingLineRepository;
import com.fantasyiq.domain.stats.DefenseVsPositionStatsRepository;
import com.fantasyiq.domain.stats.InjuryReportRepository;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.domain.stats.WeatherForecastRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that need a real Postgres instance.
 * Extend this instead of hand-rolling a Testcontainers setup in every test —
 * one real database shared across every IT test class in the run keeps
 * suites fast.
 *
 * Usage:
 *   class PlayerRepositoryIT extends IntegrationTestBase { ... }
 *
 * Deliberately NOT using @Container/@Testcontainers here: those annotations
 * tie a container's start/stop lifecycle to *each* JUnit test class's own
 * extension callbacks (start-before-all, stop-after-all, per class). Since
 * POSTGRES is one static field shared by every subclass, that meant
 * whichever IT class's lifecycle finished first would stop the container
 * out from under every sibling class that happened to run afterward --
 * intermittent "connection refused" failures in CI, isolated to whichever
 * class ran last, that got harder to reproduce locally as more IT classes
 * were added and shifted execution order. The static initializer below
 * starts it exactly once per JVM and never explicitly stops it; Testcontainers'
 * own Ryuk reaper container cleans it up when the test JVM exits.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("fantasyiq_test")
                    .withUsername("fantasyiq")
                    .withPassword("fantasyiq");

    // Same never-explicitly-stopped, shared-singleton reasoning as POSTGRES
    // above -- one Redis instance for the whole test JVM, cleaned up by
    // Testcontainers' Ryuk reaper at JVM exit.
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private RecommendationRepository recommendationRepository;
    @Autowired
    private BettingLineRepository bettingLineRepository;
    @Autowired
    private WeatherForecastRepository weatherForecastRepository;
    @Autowired
    private InjuryReportRepository injuryReportRepository;
    @Autowired
    private DefenseVsPositionStatsRepository defenseVsPositionStatsRepository;
    @Autowired
    private PlayerGameStatsRepository playerGameStatsRepository;
    @Autowired
    private PlayerExternalIdRepository playerExternalIdRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private CacheManager cacheManager;

    /**
     * Now that POSTGRES is a genuinely shared, never-restarted container
     * (see the class comment above), every mutable table needs a clean
     * slate before each test method, not just the ones a given test class
     * happens to know it personally writes to -- subclass-local @BeforeEach
     * cleanup that only wiped its own tables was silently relying on the old,
     * buggy per-class container restart to hide leftover rows from *other*
     * classes. Runs before any subclass's own @BeforeEach (JUnit executes
     * superclass lifecycle callbacks first), in FK-safe order. teams/
     * stadium_locations/team_external_ids/player_external_ids and auth
     * tables are deliberately left alone -- seeded/reconciled data, not
     * per-test fixtures, and reconciliation upserts are already idempotent
     * across runs.
     */
    @BeforeEach
    void wipeMutableTablesBeforeEachTest() {
        recommendationRepository.deleteAll();
        bettingLineRepository.deleteAll();
        weatherForecastRepository.deleteAll();
        injuryReportRepository.deleteAll();
        defenseVsPositionStatsRepository.deleteAll();
        playerGameStatsRepository.deleteAll();
        playerExternalIdRepository.deleteAll();
        gameRepository.deleteAll();
        playerRepository.deleteAll();
    }

    /**
     * Same cross-test-pollution risk the Postgres wipe above already guards
     * against, but for Redis: REDIS is one shared container for the whole
     * test JVM, so a cache entry populated by one test class would otherwise
     * leak into whatever the next test class asserts.
     */
    @BeforeEach
    void clearCachesBeforeEachTest() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }
}
