package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.ingestion.stats.StatsProvider;
import com.fantasyiq.ingestion.stats.StubStatsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

class GameIngestionServiceIT extends IntegrationTestBase {

    @TestConfiguration
    static class StubStatsProviderConfig {
        @Bean
        @Primary
        StatsProvider statsProvider() {
            return new StubStatsProvider();
        }
    }

    @Autowired
    private GameIngestionService gameIngestionService;
    @Autowired
    private GameRepository gameRepository;

    @Test
    void sameGameFromBothTeamsSchedulesIsNotDuplicated() {
        // StubStatsProvider returns 2 teams, each of whose schedule fetch
        // returns the SAME game (external id "555") -- exactly like ESPN's
        // real behavior, where a game appears in both participants' schedules.
        int gamesIngested = gameIngestionService.ingestSchedules();

        assertThat(gamesIngested).isEqualTo(1);
        assertThat(gameRepository.count()).isEqualTo(1);
    }

    @Test
    void runningIngestionTwiceDoesNotCreateDuplicateGames() {
        gameIngestionService.ingestSchedules();
        long gamesAfterFirstRun = gameRepository.count();

        gameIngestionService.ingestSchedules();
        long gamesAfterSecondRun = gameRepository.count();

        assertThat(gamesAfterSecondRun).isEqualTo(gamesAfterFirstRun);
    }
}
