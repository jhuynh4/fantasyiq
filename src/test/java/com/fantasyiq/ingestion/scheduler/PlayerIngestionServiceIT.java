package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.ingestion.stats.StatsProvider;
import com.fantasyiq.ingestion.stats.StubStatsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerIngestionServiceIT extends IntegrationTestBase {

    @TestConfiguration
    static class StubStatsProviderConfig {
        @Bean
        @Primary
        StatsProvider statsProvider() {
            return new StubStatsProvider();
        }
    }

    @Autowired
    private PlayerIngestionService playerIngestionService;
    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void runningIngestionTwiceDoesNotCreateDuplicatePlayers() {
        int firstRunCount = playerIngestionService.ingestRosters();
        long playersAfterFirstRun = playerRepository.count();

        int secondRunCount = playerIngestionService.ingestRosters();
        long playersAfterSecondRun = playerRepository.count();

        assertThat(firstRunCount).isEqualTo(1);
        assertThat(secondRunCount).isEqualTo(1);
        assertThat(playersAfterSecondRun).isEqualTo(playersAfterFirstRun);
    }
}
