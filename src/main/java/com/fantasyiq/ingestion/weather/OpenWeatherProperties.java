package com.fantasyiq.ingestion.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fantasyiq.providers.openweather")
public record OpenWeatherProperties(String baseUrl, String apiKey) {
}
