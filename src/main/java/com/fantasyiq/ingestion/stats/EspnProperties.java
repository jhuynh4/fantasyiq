package com.fantasyiq.ingestion.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fantasyiq.providers.espn")
public record EspnProperties(String baseUrl, String coreBaseUrl, String webBaseUrl) {
}
