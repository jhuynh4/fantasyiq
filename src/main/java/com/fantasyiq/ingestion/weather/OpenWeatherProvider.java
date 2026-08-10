package com.fantasyiq.ingestion.weather;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Component
public class OpenWeatherProvider implements WeatherProvider {

    private static final String RESILIENCE_INSTANCE = "weatherApi";

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;

    public OpenWeatherProvider(RestClient.Builder restClientBuilder, OpenWeatherProperties openWeatherProperties) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = openWeatherProperties.baseUrl();
        this.apiKey = openWeatherProperties.apiKey();
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fetchForecastFallback")
    public Optional<RawWeatherForecast> fetchForecast(double latitude, double longitude, Instant kickoff) {
        OpenWeatherForecastResponse response = restClient.get()
                .uri(baseUrl + "/forecast?lat={lat}&lon={lon}&appid={key}&units=imperial",
                        String.format(Locale.ROOT, "%.6f", latitude),
                        String.format(Locale.ROOT, "%.6f", longitude),
                        apiKey)
                .retrieve()
                .body(OpenWeatherForecastResponse.class);
        return OpenWeatherResponseMapper.toRawWeatherForecast(response, kickoff);
    }

    private Optional<RawWeatherForecast> fetchForecastFallback(double latitude, double longitude,
                                                                 Instant kickoff, Throwable t) {
        throw new WeatherUnavailableException(
                "OpenWeatherMap forecast endpoint unavailable for (" + latitude + ", " + longitude + ")", t);
    }
}
