package com.fantasyiq.ingestion.odds;

import com.fantasyiq.IntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same shape/rationale as EspnStatsProviderContractTest/OpenWeatherProviderContractTest:
 * exercises the real Spring-proxied TheOddsApiProvider bean against a WireMock
 * stand-in so @Retry/@CircuitBreaker are genuinely tested.
 */
class TheOddsApiProviderContractTest extends IntegrationTestBase {

    private static WireMockServer wireMock;

    @Autowired
    private OddsProvider oddsProvider;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @AfterEach
    void resetStubsAndCircuitBreaker() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("oddsApi").reset();
    }

    @DynamicPropertySource
    static void oddsApiBaseUrl(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(0);
        wireMock.start();
        registry.add("fantasyiq.providers.odds-api.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Test
    void parsesRealCapturedOddsPayload() {
        wireMock.stubFor(get(urlPathEqualTo("/sports/americanfootball_nfl/odds")).willReturn(okJson(ODDS_PAYLOAD)));

        List<RawGameOdds> odds = oddsProvider.fetchCurrentOdds();

        assertThat(odds).containsExactly(new RawGameOdds("Kansas City Chiefs", "Baltimore Ravens",
                Instant.parse("2026-09-07T17:00:00Z"), "draftkings",
                new BigDecimal("-3.5"), new BigDecimal("3.5"), new BigDecimal("45.5")));
    }

    @Test
    void repeatedFailuresEventuallyTripTheCircuitBreaker() {
        wireMock.stubFor(get(urlPathEqualTo("/sports/americanfootball_nfl/odds")).willReturn(aResponse().withStatus(503)));

        RuntimeException lastException = null;
        for (int i = 0; i < 10; i++) {
            try {
                oddsProvider.fetchCurrentOdds();
            } catch (RuntimeException e) {
                lastException = e;
            }
        }

        assertThat(lastException).isInstanceOf(OddsUnavailableException.class);
    }

    private static final String ODDS_PAYLOAD = """
            [
                {"id":"evt-1","commence_time":"2026-09-07T17:00:00Z",
                 "home_team":"Kansas City Chiefs","away_team":"Baltimore Ravens",
                 "bookmakers":[
                    {"key":"draftkings","markets":[
                        {"key":"spreads","outcomes":[
                            {"name":"Kansas City Chiefs","point":-3.5},
                            {"name":"Baltimore Ravens","point":3.5}
                        ]},
                        {"key":"totals","outcomes":[
                            {"name":"Over","point":45.5},
                            {"name":"Under","point":45.5}
                        ]}
                    ]}
                 ]}
            ]
            """;
}
