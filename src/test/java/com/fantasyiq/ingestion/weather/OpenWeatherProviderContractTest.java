package com.fantasyiq.ingestion.weather;

import com.fantasyiq.IntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same shape/rationale as EspnStatsProviderContractTest: exercises the real
 * Spring-proxied OpenWeatherProvider bean against a WireMock stand-in so
 * @Retry/@CircuitBreaker are genuinely tested, without a real
 * OPENWEATHER_API_KEY (which this project doesn't have yet -- see
 * CURRENT_WORK.md). Resilience assertion deliberately checks only the
 * eventual outcome, not exact request counts -- see that class's own
 * comment for why.
 */
class OpenWeatherProviderContractTest extends IntegrationTestBase {

    private static WireMockServer wireMock;

    @Autowired
    private WeatherProvider weatherProvider;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @AfterEach
    void resetStubsAndCircuitBreaker() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("weatherApi").reset();
    }

    @DynamicPropertySource
    static void openWeatherBaseUrl(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(0);
        wireMock.start();
        registry.add("fantasyiq.providers.openweather.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Test
    void parsesRealCapturedForecastPayload() {
        wireMock.stubFor(get(urlPathEqualTo("/forecast")).willReturn(okJson(FORECAST_PAYLOAD)));

        Optional<RawWeatherForecast> forecast = weatherProvider.fetchForecast(
                44.5013, -88.0622, Instant.ofEpochSecond(1_700_002_000L));

        assertThat(forecast).contains(new RawWeatherForecast(38, 14, 55, "light rain"));
    }

    @Test
    void repeatedFailuresEventuallyTripTheCircuitBreaker() {
        wireMock.stubFor(get(urlPathEqualTo("/forecast")).willReturn(aResponse().withStatus(503)));

        RuntimeException lastException = null;
        for (int i = 0; i < 10; i++) {
            try {
                weatherProvider.fetchForecast(44.5013, -88.0622, Instant.now());
            } catch (RuntimeException e) {
                lastException = e;
            }
        }

        assertThat(lastException).isInstanceOf(WeatherUnavailableException.class);
    }

    private static final String FORECAST_PAYLOAD = """
            {"list":[
                {"dt":1700000000,"main":{"temp":52.1},"wind":{"speed":9.0},"pop":0.1,"weather":[{"description":"clear sky"}]},
                {"dt":1700002000,"main":{"temp":38.4},"wind":{"speed":14.2},"pop":0.55,"weather":[{"description":"light rain"}]}
            ]}
            """;
}
