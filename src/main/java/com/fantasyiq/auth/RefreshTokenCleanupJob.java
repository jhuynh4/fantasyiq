package com.fantasyiq.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Every login and every refresh adds a row, and nothing removed them. Only expired
 * tokens are deleted: a revoked token that has not expired yet stays until it does,
 * so presenting a rotated-out token is still told apart from presenting an unknown one.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void scheduledCleanup() {
        try {
            int deleted = deleteExpired(Instant.now());
            log.info("Refresh token cleanup complete: {} expired tokens deleted", deleted);
        } catch (Exception e) {
            log.error("Refresh token cleanup failed", e);
        }
    }

    @Transactional
    public int deleteExpired(Instant now) {
        return refreshTokenRepository.deleteByExpiresAtBefore(now);
    }
}
