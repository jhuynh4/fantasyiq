package com.fantasyiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for FantasyIQ.
 *
 * Scheduling is enabled at the application level because ingestion jobs
 * (Phase 2+) run as Spring @Scheduled methods rather than a separate process.
 */
@SpringBootApplication
@EnableScheduling
public class FantasyIqApplication {

    public static void main(String[] args) {
        SpringApplication.run(FantasyIqApplication.class, args);
    }
}
