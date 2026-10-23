package com.fantasyiq.ingestion.injuries;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.ingestion.stats.EspnUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Same shape as EspnStatsProviderContractTest, for the injury adapter: a captured
 * roster payload parsed through the real Spring-proxied bean, and a failing or hung
 * ESPN ending in a typed failure. Assertions stay on eventual outcomes, not on how
 * Retry and CircuitBreaker interleave.
 */
class EspnInjuryProviderContractTest extends IntegrationTestBase {

    private static WireMockServer wireMock;

    @Autowired
    private InjuryProvider injuryProvider;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    // The espnApi breaker is shared with the stats adapter and is a singleton in the cached context.
    @AfterEach
    void resetStubsAndCircuitBreaker() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("espnApi").reset();
    }

    @DynamicPropertySource
    static void espnBaseUrl(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(0);
        wireMock.start();
        registry.add("fantasyiq.providers.espn.base-url", () -> "http://localhost:" + wireMock.port());
        registry.add("fantasyiq.http-client.read-timeout", () -> "1s");
    }

    @Test
    void parsesInjuriesFromACapturedRosterPayloadAndSkipsHealthyAthletes() {
        wireMock.stubFor(get(urlEqualTo("/teams/22/roster")).willReturn(okJson(ROSTER_PAYLOAD)));

        List<RawInjuryReport> reports = injuryProvider.fetchCurrentInjuries("22");

        assertThat(reports).containsExactly(
                new RawInjuryReport("101", "QUESTIONABLE", LocalDate.of(2026, 10, 21)),
                new RawInjuryReport("103", "INJURED RESERVE", LocalDate.of(2026, 9, 30)));
    }

    @Test
    void repeatedFailuresEventuallyBecomeATypedFailure() {
        wireMock.stubFor(get(urlEqualTo("/teams/22/roster")).willReturn(aResponse().withStatus(503)));

        RuntimeException lastException = null;
        for (int i = 0; i < 10; i++) {
            try {
                injuryProvider.fetchCurrentInjuries("22");
            } catch (RuntimeException e) {
                lastException = e;
            }
        }

        assertThat(lastException).isInstanceOf(EspnUnavailableException.class);
    }

    @Test
    void aVendorThatNeverAnswersBecomesATypedFailureNotAHang() {
        wireMock.stubFor(get(urlEqualTo("/teams/22/roster")).willReturn(aResponse().withStatus(200).withFixedDelay(5_000)));

        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            assertThatThrownBy(() -> injuryProvider.fetchCurrentInjuries("22"))
                    .isInstanceOf(EspnUnavailableException.class);
        });
    }

    private static final String ROSTER_PAYLOAD = """
            {"athletes":[
              {"position":"offense","items":[
                {"id":"101","fullName":"Player One","injuries":[{"status":"Questionable","date":"2026-10-21T18:05Z"}]},
                {"id":"102","fullName":"Player Two","injuries":[]}
              ]},
              {"position":"defense","items":[
                {"id":"103","fullName":"Player Three","injuries":[{"status":"Injured Reserve","date":"2026-09-30T12:00Z"}]}
              ]}
            ]}
            """;
}
