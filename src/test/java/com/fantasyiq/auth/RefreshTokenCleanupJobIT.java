package com.fantasyiq.auth;

import com.fantasyiq.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCleanupJobIT extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RefreshTokenCleanupJob cleanupJob;

    private User user;

    @AfterEach
    void removeFixtures() {
        if (user != null) {
            refreshTokenRepository.deleteAll(refreshTokenRepository.findAll().stream()
                    .filter(t -> t.getUser().getId().equals(user.getId())).toList());
            userRepository.delete(user);
        }
    }

    @Test
    void deletesOnlyExpiredTokens() {
        user = userRepository.save(new User("cleanup-" + UUID.randomUUID() + "@fantasyiq.dev", "hash", "Cleanup"));
        Instant now = Instant.now();
        refreshTokenRepository.save(new RefreshToken(user, "expired", now.minus(1, ChronoUnit.DAYS)));
        RefreshToken revokedButValid = new RefreshToken(user, "revoked-valid", now.plus(5, ChronoUnit.DAYS));
        revokedButValid.revoke();
        refreshTokenRepository.save(revokedButValid);
        refreshTokenRepository.save(new RefreshToken(user, "valid", now.plus(5, ChronoUnit.DAYS)));

        int deleted = cleanupJob.deleteExpired(now);

        assertThat(deleted).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("expired")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("revoked-valid")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash("valid")).isPresent();
    }
}
