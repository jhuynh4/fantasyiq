package com.fantasyiq.ingestion.stats;

import com.fantasyiq.IntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asserts EspnStatsProvider correctly parses a real captured payload shape
 * through the actual Spring-proxied bean (not a hand-built instance), so
 * the @Retry/@CircuitBreaker annotations are genuinely exercised, and that
 * retry/circuit-breaker behavior triggers on repeated 5xx responses --
 * without ever hitting the real ESPN API.
 *
 * The retry/circuit-breaker tests deliberately avoid asserting exact
 * request counts or which specific exception type surfaces at which
 * attempt -- Retry and CircuitBreaker interact (each retry attempt is
 * separately visible to the circuit breaker), and pinning down the exact
 * interleaving is re-testing Resilience4j's own internals, not our wiring.
 * What actually matters here: failures get retried at all, and enough
 * repeated failures eventually trip the breaker to its fallback.
 */
class EspnStatsProviderContractTest extends IntegrationTestBase {

    private static WireMockServer wireMock;

    @Autowired
    private StatsProvider statsProvider;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    /**
     * The circuit breaker's state is a singleton in the (cached, shared
     * across this class's test methods) Spring context -- without an
     * explicit reset, repeatedFailuresEventuallyTripTheCircuitBreaker
     * tripping it would leave it open and break whichever other test
     * happens to run afterward, since JUnit doesn't guarantee method order.
     */
    @AfterEach
    void resetStubsAndCircuitBreaker() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("espnApi").reset();
    }

    /**
     * @DynamicPropertySource runs during context preparation, before any
     * @BeforeAll -- so the server has to be created and started here,
     * the one place this class ever calls "new WireMockServer(...)",
     * rather than in a separate @BeforeAll that would race it and leave
     * Spring pointed at a different (stale) instance's port.
     */
    @DynamicPropertySource
    static void espnBaseUrl(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(0);
        wireMock.start();
        registry.add("fantasyiq.providers.espn.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Test
    void parsesRealCapturedTeamsPayload() {
        wireMock.stubFor(get(urlEqualTo("/teams")).willReturn(okJson(TEAMS_PAYLOAD)));

        List<RawTeam> teams = statsProvider.fetchTeams();

        assertThat(teams).containsExactly(new RawTeam("22", "ARI", "Arizona Cardinals"));
    }

    @Test
    void retriesTransientServerErrorsBeforeGivingUp() {
        wireMock.stubFor(get(urlEqualTo("/teams")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> statsProvider.fetchTeams()).isInstanceOf(RuntimeException.class);

        // max-attempts: 3 (application.yml / application-test.yml) -- a
        // persistent 503 should be retried, not fail after a single call.
        assertThat(wireMock.findAll(getRequestedFor(urlEqualTo("/teams"))).size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void repeatedFailuresEventuallyTripTheCircuitBreaker() {
        wireMock.stubFor(get(urlEqualTo("/teams")).willReturn(aResponse().withStatus(500)));

        RuntimeException lastException = null;
        for (int i = 0; i < 6; i++) {
            try {
                statsProvider.fetchTeams();
            } catch (RuntimeException e) {
                lastException = e;
            }
        }

        assertThat(lastException).isInstanceOf(EspnUnavailableException.class);
    }

    private static final String TEAMS_PAYLOAD = """
            {"sports":[{"leagues":[{"teams":[
                {"team":{"id":"22","abbreviation":"ARI","displayName":"Arizona Cardinals"}}
            ]}]}]}
            """;
}
