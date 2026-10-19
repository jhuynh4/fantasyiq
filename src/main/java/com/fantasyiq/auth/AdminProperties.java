package com.fantasyiq.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Both blank by default (nothing is created); set ADMIN_EMAIL and
 * ADMIN_PASSWORD in an environment to have AdminBootstrap create that
 * account at startup.
 */
@ConfigurationProperties(prefix = "fantasyiq.admin")
public record AdminProperties(String email, String password) {
}
