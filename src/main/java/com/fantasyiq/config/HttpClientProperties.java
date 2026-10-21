package com.fantasyiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for every outbound vendor call (ESPN, OpenWeatherMap, The Odds API).
 *
 * Both timeouts must be set and positive -- a missing or zero value would
 * silently mean "wait forever" for some HTTP clients, which is exactly the
 * failure these exist to prevent, so it fails at startup instead.
 *
 * userAgent is required too, because the default is not safe to leave to the
 * HTTP client: with no header set, the JDK's older connection class announces
 * itself as "Java/21.x", which ESPN's edge answers with 403 Access Denied when
 * the request comes from a cloud address (found on the first AWS deployment;
 * from a home network it was never blocked).
 */
@ConfigurationProperties(prefix = "fantasyiq.http-client")
public record HttpClientProperties(Duration connectTimeout, Duration readTimeout, String userAgent) {

    public HttpClientProperties {
        requirePositive("fantasyiq.http-client.connect-timeout", connectTimeout);
        requirePositive("fantasyiq.http-client.read-timeout", readTimeout);
        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException("fantasyiq.http-client.user-agent must not be blank");
        }
    }

    private static void requirePositive(String property, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(property + " must be a positive duration, was: " + value);
        }
    }
}
