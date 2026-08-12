package com.fantasyiq;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

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

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
