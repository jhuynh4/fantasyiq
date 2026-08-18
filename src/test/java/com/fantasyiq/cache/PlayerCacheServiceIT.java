package com.fantasyiq.cache;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.player.PlayerSnapshot;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerCacheServiceIT extends IntegrationTestBase {

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private PlayerCacheService playerCacheService;

    @Test
    void getByIdServesFromCacheOnceLoaded() {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Player player = playerRepository.save(new Player("Cache Test RB", "RB", ari, 21, "ACTIVE", LocalDate.of(1999, 5, 5)));
        UUID id = player.getId();

        assertThat(playerCacheService.getById(id)).isNotNull();

        // Deleting straight from the DB, bypassing refresh entirely -- if
        // the second read still finds the player, it can only be the cache.
        playerRepository.deleteAll();
        assertThat(playerCacheService.getById(id)).isNotNull();
    }

    @Test
    void refreshOverwritesAStaleCacheEntryWithCurrentData() {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Player player = playerRepository.save(new Player("Refresh Test RB", "RB", ari, 22, "ACTIVE", LocalDate.of(1999, 5, 5)));
        playerCacheService.getById(player.getId());

        player.updateFrom(player.getFullName(), player.getPosition(), player.getCurrentTeam(),
                player.getJerseyNumber(), "INJURED_RESERVE", player.getBirthDate());
        playerRepository.save(player);

        PlayerSnapshot refreshed = playerCacheService.refresh(player);
        assertThat(refreshed.status()).isEqualTo("INJURED_RESERVE");
        assertThat(playerCacheService.getById(player.getId()).status()).isEqualTo("INJURED_RESERVE");
    }

    @Test
    void notFoundResultIsHandledWithoutError() {
        assertThat(playerCacheService.getById(UUID.randomUUID())).isNull();
    }
}
