package com.fantasyiq.ingestion.weather;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OpenWeatherResponseMapperTest {

    @Test
    void picksTheForecastEntryClosestToKickoff() {
        OpenWeatherForecastResponse response = new OpenWeatherForecastResponse(List.of(
                entry(1_000_000_000L, 60.0, 5.0, 0.1, "clear sky"),
                // closest to kickoff (1_000_010_000)
                entry(1_000_009_000L, 45.0, 12.0, 0.6, "light rain"),
                entry(1_000_020_000L, 40.0, 15.0, 0.8, "moderate rain")));

        Optional<RawWeatherForecast> forecast = OpenWeatherResponseMapper.toRawWeatherForecast(
                response, Instant.ofEpochSecond(1_000_010_000L));

        assertThat(forecast).contains(new RawWeatherForecast(45, 12, 60, "light rain"));
    }

    @Test
    void emptyOrMissingListYieldsEmptyOptional() {
        assertThat(OpenWeatherResponseMapper.toRawWeatherForecast(null, Instant.now())).isEmpty();
        assertThat(OpenWeatherResponseMapper.toRawWeatherForecast(
                new OpenWeatherForecastResponse(List.of()), Instant.now())).isEmpty();
        assertThat(OpenWeatherResponseMapper.toRawWeatherForecast(
                new OpenWeatherForecastResponse(null), Instant.now())).isEmpty();
    }

    private static OpenWeatherEntry entry(long dt, double temp, double windSpeed, double pop, String description) {
        return new OpenWeatherEntry(dt, new OpenWeatherMain(temp), new OpenWeatherWind(windSpeed),
                pop, List.of(new OpenWeatherCondition(description)));
    }
}
