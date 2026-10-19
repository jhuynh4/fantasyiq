package com.fantasyiq.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-value-thats-reasonably-long";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 15, 30));

    @Test
    void generatesTokenThatRoundTripsTheUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "player@fantasyiq.dev", UserRole.USER);

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
        String token = otherService.generateAccessToken(UUID.randomUUID(), "x@y.com", UserRole.USER);

        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void roundTripsTheRole() {
        String adminToken = jwtService.generateAccessToken(UUID.randomUUID(), "admin@fantasyiq.dev", UserRole.ADMIN);
        String userToken = jwtService.generateAccessToken(UUID.randomUUID(), "user@fantasyiq.dev", UserRole.USER);

        assertThat(jwtService.extractRole(adminToken)).isEqualTo(UserRole.ADMIN);
        assertThat(jwtService.extractRole(userToken)).isEqualTo(UserRole.USER);
    }

    @Test
    void aTokenWithNoRoleClaimIsTreatedAsAPlainUser() throws Exception {
        String legacyToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(signingKey())
                .compact();

        assertThat(jwtService.isValid(legacyToken)).isTrue();
        assertThat(jwtService.extractRole(legacyToken)).isEqualTo(UserRole.USER);
    }

    @Test
    void anUnrecognizedRoleClaimIsTreatedAsAPlainUserNotAnAdmin() throws Exception {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "SUPERUSER")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(signingKey())
                .compact();

        assertThat(jwtService.extractRole(token)).isEqualTo(UserRole.USER);
    }

    // Mirrors JwtService.deriveKey so a test can hand-build tokens the service itself never would.
    private static SecretKey signingKey() throws Exception {
        byte[] hashed = MessageDigest.getInstance("SHA-256").digest(SECRET.getBytes(StandardCharsets.UTF_8));
        return Keys.hmacShaKeyFor(hashed);
    }
}
