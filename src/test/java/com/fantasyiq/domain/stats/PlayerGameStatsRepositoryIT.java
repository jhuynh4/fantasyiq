package com.fantasyiq.domain.stats;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * findTop4ByPlayerOrderByGame_SeasonDescGame_WeekDesc backs the /trending
 * endpoint's "most recent games regardless of season" lookup -- the one
 * genuinely new piece of logic in that slice (the calculator it feeds is
 * already covered by UsageTrendFactorCalculatorTest). Uses two distinctive
 * fake seasons (2098/2099) specifically to prove it correctly crosses a
 * season boundary, not just orders within one.
 */
class PlayerGameStatsRepositoryIT extends IntegrationTestBase {

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private PlayerGameStatsRepository playerGameStatsRepository;

    @Test
    void returnsTheFourMostRecentGamesAcrossSeasonsMostRecentFirst() {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Team sf = teamRepository.findByAbbreviation("SF").orElseThrow();
        Player player = playerRepository.save(new Player("Trending WR", "WR", ari, 14, "ACTIVE", LocalDate.of(1997, 1, 1)));

        // 2098 weeks 16-17, then 2099 weeks 1-2 -- the most recent 4 games
        // overall span a season boundary, oldest to newest across both years.
        // targets is given a distinct, order-identifying value per row so
        // the assertion below can verify ordering via a directly-mapped
        // column rather than game (a LAZY association that would throw
        // LazyInitializationException once accessed outside the repository
        // call's now-closed persistence context).
        saveStats(player, ari, sf, 2098, 16, 5);
        saveStats(player, ari, sf, 2098, 17, 6);
        saveStats(player, ari, sf, 2099, 1, 7);
        saveStats(player, ari, sf, 2099, 2, 8);
        // A 5th, older game that must NOT be included in the top-4 window
        saveStats(player, ari, sf, 2098, 15, 1);

        List<PlayerGameStats> mostRecent = playerGameStatsRepository
                .findTop4ByPlayerOrderByGame_SeasonDescGame_WeekDesc(player);

        assertThat(mostRecent).hasSize(4);
        assertThat(mostRecent).extracting(PlayerGameStats::getTargets)
                .containsExactly(8, 7, 6, 5);
    }

    private void saveStats(Player player, Team offenseTeam, Team opponent, int season, int week, int targets) {
        Game game = gameRepository.save(new Game("trend-" + season + "-" + week, season, week, offenseTeam, opponent,
                Instant.parse(season + "-09-07T17:00:00Z"), "Test Stadium", "FINAL"));
        playerGameStatsRepository.save(new PlayerGameStats(player, game, offenseTeam,
                targets, targets - 1, 40, 0, 0, null, null, null, null, null, 0,
                BigDecimal.TEN, BigDecimal.TEN));
    }
}
