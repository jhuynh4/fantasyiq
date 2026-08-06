package com.fantasyiq.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new JwtProperties("test-secret-value-thats-reasonably-long", 15, 30));

    @Test
    void generatesTokenThatRoundTripsTheUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "player@fantasyiq.dev");

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void rejectsGarbageTokens() {
        assertThat(jwtService.isValid("not-a-real-token")).isFalse();
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtService otherService = new JwtService(
                new JwtProperties("a-completely-different-secret-value", 15, 30));
        String token = otherService.generateAccessToken(UUID.randomUUID(), "x@y.com");

        assertThat(jwtService.isValid(token)).isFalse();
    }
}
