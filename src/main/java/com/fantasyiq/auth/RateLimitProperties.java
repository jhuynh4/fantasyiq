package com.fantasyiq.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param forwardedHops how many proxies in front of the app append to X-Forwarded-For (0 locally,
 *                      2 behind CloudFront and the load balancer); see ClientAddress
 */
@ConfigurationProperties(prefix = "fantasyiq.rate-limit.auth")
public record RateLimitProperties(int capacity, long periodSeconds, int forwardedHops) {
}
