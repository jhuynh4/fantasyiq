package com.fantasyiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Timeouts for every outbound vendor call (ESPN, OpenWeatherMap, The Odds
 * API). Both must be set and positive -- a missing or zero value would
 * silently mean "wait forever" for some HTTP clients, which is exactly the
 * failure these exist to prevent, so it fails at startup instead.
 */
@ConfigurationProperties(prefix = "fantasyiq.http-client")
public record HttpClientProperties(Duration connectTimeout, Duration readTimeout) {

    public HttpClientProperties {
        requirePositive("fantasyiq.http-client.connect-timeout", connectTimeout);
        requirePositive("fantasyiq.http-client.read-timeout", readTimeout);
    }

    private static void requirePositive(String property, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(property + " must be a positive duration, was: " + value);
        }
    }
}
