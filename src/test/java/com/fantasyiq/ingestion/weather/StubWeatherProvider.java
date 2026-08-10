package com.fantasyiq.ingestion.weather;

import java.time.Instant;
import java.util.Optional;

public class StubWeatherProvider implements WeatherProvider {

    @Override
    public Optional<RawWeatherForecast> fetchForecast(double latitude, double longitude, Instant kickoff) {
        return Optional.of(new RawWeatherForecast(72, 8, 20, "clear sky"));
    }
}
