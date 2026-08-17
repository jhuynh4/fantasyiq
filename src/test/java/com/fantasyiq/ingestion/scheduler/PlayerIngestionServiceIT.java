package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.cache.PlayerCacheService;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerExternalIdRepository;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.player.PlayerSnapshot;
import com.fantasyiq.ingestion.stats.StatsProvider;
import com.fantasyiq.ingestion.stats.StubStatsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

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
    @Autowired
    private PlayerCacheService playerCacheService;
    @Autowired
    private PlayerExternalIdRepository playerExternalIdRepository;

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

    @Test
    void ingestionPopulatesThePlayerCacheWithoutWaitingForARead() {
        playerIngestionService.ingestRosters();
        List<Player> players = playerRepository.findAll();
        assertThat(players).hasSize(1);

        // Delete straight from the DB, bypassing any cache refresh -- if the
        // cache lookup below still finds the player, it can only have come
        // from the population that happened during ingestion itself.
        // player_external_ids must go first -- it FK-references players,
        // and this run's own ingestion just created a row there (the ESPN
        // id reconciled during resolveOrCreateFromEspn).
        playerExternalIdRepository.deleteAll();
        playerRepository.deleteAll();

        PlayerSnapshot cached = playerCacheService.getById(players.get(0).getId());
        assertThat(cached).isNotNull();
        assertThat(cached.fullName()).isEqualTo("Test Player");
    }
}
