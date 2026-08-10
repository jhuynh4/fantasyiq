package com.fantasyiq.ingestion.weather;

public record RawWeatherForecast(Integer temperatureF, Integer windMph, Integer precipitationPct, String conditions) {
}
