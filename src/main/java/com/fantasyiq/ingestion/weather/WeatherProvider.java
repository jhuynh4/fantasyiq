package com.fantasyiq.ingestion.weather;

import java.time.Instant;
import java.util.Optional;

public interface WeatherProvider {

    Optional<RawWeatherForecast> fetchForecast(double latitude, double longitude, Instant kickoff);
}
