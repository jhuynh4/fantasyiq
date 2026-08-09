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
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asserts EspnStatsProvider correctly parses a real captured payload shape
 * through the actual Spring-proxied bean (not a hand-built instance), so
 * the @Retry/@CircuitBreaker annotations are genuinely exercised, and that
 * retry/circuit-breaker behavior triggers on repeated 5xx responses --
 * without ever hitting the real ESPN API.
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
     * explicit reset, circuitBreakerFallbackThrowsAfterRepeatedFailures
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
    void retriesOnServerErrorThenSucceeds() {
        wireMock.stubFor(get(urlEqualTo("/teams"))
                .inScenario("retry-then-succeed")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second-attempt"));
        wireMock.stubFor(get(urlEqualTo("/teams"))
                .inScenario("retry-then-succeed")
                .whenScenarioStateIs("second-attempt")
                .willReturn(okJson(TEAMS_PAYLOAD)));

        List<RawTeam> teams = statsProvider.fetchTeams();

        assertThat(teams).isNotEmpty();
        wireMock.verify(2, getRequestedFor(urlEqualTo("/teams")));
    }

    @Test
    void circuitBreakerFallbackThrowsAfterRepeatedFailures() {
        wireMock.stubFor(get(urlEqualTo("/teams")).willReturn(aResponse().withStatus(500)));

        // application-test.yml sets minimum-number-of-calls: 4 for this
        // instance specifically so this loop stays short and fast.
        for (int i = 0; i < 4; i++) {
            try {
                statsProvider.fetchTeams();
            } catch (RuntimeException ignored) {
                // expected on every attempt here -- either the real 500 (via
                // retry exhaustion) or, once the breaker trips, the fallback
            }
        }

        assertThatThrownBy(() -> statsProvider.fetchTeams())
                .isInstanceOf(EspnUnavailableException.class);
    }

    private static final String TEAMS_PAYLOAD = """
            {"sports":[{"leagues":[{"teams":[
                {"team":{"id":"22","abbreviation":"ARI","displayName":"Arizona Cardinals"}}
            ]}]}]}
            """;
}
