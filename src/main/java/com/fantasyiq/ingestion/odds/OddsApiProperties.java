package com.fantasyiq.ingestion.odds;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fantasyiq.providers.odds-api")
public record OddsApiProperties(String baseUrl, String apiKey) {
}
