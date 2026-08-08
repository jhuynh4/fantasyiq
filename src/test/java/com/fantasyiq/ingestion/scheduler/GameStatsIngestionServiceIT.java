package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.ingestion.stats.StatsProvider;
import com.fantasyiq.ingestion.stats.StubStatsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

class GameStatsIngestionServiceIT extends IntegrationTestBase {

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
    private GameIngestionService gameIngestionService;
    @Autowired
    private GameStatsIngestionService gameStatsIngestionService;
    @Autowired
    private PlayerGameStatsRepository playerGameStatsRepository;
    @Autowired
    private GameRepository gameRepository;

    /**
     * Test methods in this class share one Postgres instance with no
     * rollback between them (see IntegrationTestBase), and JUnit doesn't
     * guarantee method execution order. skipsStatLinesForGamesNotYetIngested
     * specifically depends on game "555" NOT existing yet -- without this
     * cleanup, running after a test that already created it (e.g.
     * ingestsStatsForAKnownPlayerAndGame) would silently invalidate that
     * assumption and fail nondeterministically depending on order.
     */
    @BeforeEach
    void cleanUp() {
        playerGameStatsRepository.deleteAll();
        gameRepository.deleteAll();
    }

    @Test
    void ingestsStatsForAKnownPlayerAndGame() {
        playerIngestionService.ingestRosters(); // seeds "Test Player" (WR, ESPN id 12345)
        gameIngestionService.ingestSchedules(2026); // seeds game external_ref "555"

        int statLinesIngested = gameStatsIngestionService.ingestGameStats(2025);

        assertThat(statLinesIngested).isEqualTo(1);
        assertThat(playerGameStatsRepository.count()).isEqualTo(1);
    }

    @Test
    void runningTwiceDoesNotDuplicateTheSameStatLine() {
        playerIngestionService.ingestRosters();
        gameIngestionService.ingestSchedules(2026);

        gameStatsIngestionService.ingestGameStats(2025);
        long afterFirstRun = playerGameStatsRepository.count();

        gameStatsIngestionService.ingestGameStats(2025);
        long afterSecondRun = playerGameStatsRepository.count();

        assertThat(afterSecondRun).isEqualTo(afterFirstRun);
    }

    @Test
    void skipsStatLinesForGamesNotYetIngested() {
        // Player exists, but the game ("555") was never ingested via schedule ingestion
        playerIngestionService.ingestRosters();

        int statLinesIngested = gameStatsIngestionService.ingestGameStats(2025);

        assertThat(statLinesIngested).isEqualTo(0);
        assertThat(playerGameStatsRepository.count()).isEqualTo(0);
    }
}
