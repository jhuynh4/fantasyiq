package com.fantasyiq;

import org.junit.jupiter.api.Test;

/**
 * Deliberately minimal context-load smoke test. Extend IntegrationTestBase
 * once real components (JPA repos, etc.) exist so the context has a real
 * Postgres to bind to; until then this just confirms the module compiles
 * and the test toolchain (JUnit 5) is wired up correctly.
 */
class FantasyIqApplicationSmokeTest {

    @Test
    void placeholderUntilFirstRealComponentExists() {
        // Replace with @SpringBootTest extends IntegrationTestBase once
        // Phase 1's first JPA entity/repository lands.
        assert true;
    }
}
