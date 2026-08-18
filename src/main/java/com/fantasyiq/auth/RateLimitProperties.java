package com.fantasyiq.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fantasyiq.rate-limit.auth")
public record RateLimitProperties(int capacity, long periodSeconds) {
}
