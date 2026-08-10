package com.fantasyiq.domain.stats;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real DB fixtures rather than a stub -- this service is pure aggregation
 * over already-ingested data, not an ESPN adapter, so there's no vendor
 * response to fake. Uses a distinctive season (2099) so this test's rows
 * can never collide with anything another test or a real ingestion run
 * writes into the shared Testcontainers Postgres instance.
 */
class DefenseVsPositionStatsServiceIT extends IntegrationTestBase {

    private static final int SEASON = 2099;
    private static final int WEEK = 1;

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private PlayerGameStatsRepository playerGameStatsRepository;
    @Autowired
    private DefenseVsPositionStatsRepository defenseVsPositionStatsRepository;
    @Autowired
    private DefenseVsPositionStatsService defenseVsPositionStatsService;

    @BeforeEach
    void cleanUp() {
        defenseVsPositionStatsRepository.deleteAll();
        playerGameStatsRepository.deleteAll();
        gameRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void ranksDefensesByFantasyPointsAllowedWithinAPosition() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Team dal = team("DAL");
        Team nyg = team("NYG");

        // Game 1: ARI @ SF -- a WR on ARI's offense scores big against SF's defense
        Game game1 = saveGame("dvp-game-1", ari, sf);
        Player wr1 = savePlayer("Big Game WR", "WR");
        savePlayerGameStats(wr1, game1, ari, new BigDecimal("20.00"), new BigDecimal("15.00"));

        // Game 2: DAL @ NYG -- a WR on DAL's offense has a quiet day against NYG's defense
        Game game2 = saveGame("dvp-game-2", dal, nyg);
        Player wr2 = savePlayer("Quiet Day WR", "WR");
        savePlayerGameStats(wr2, game2, dal, new BigDecimal("8.00"), new BigDecimal("6.00"));

        int rowsWritten = defenseVsPositionStatsService.computeForWeek(SEASON, WEEK);

        // 2 defenses (SF, NYG) each got exactly one WR row this week
        assertThat(rowsWritten).isEqualTo(2);

        DefenseVsPositionStats sfVsWr = findRow(sf, "WR");
        DefenseVsPositionStats nygVsWr = findRow(nyg, "WR");

        assertThat(sfVsWr.getFantasyPointsAllowedPpr()).isEqualByComparingTo("20.00");
        assertThat(nygVsWr.getFantasyPointsAllowedPpr()).isEqualByComparingTo("8.00");

        // NYG allowed fewer points -> tougher matchup -> rank 1; SF allowed more -> rank 2
        assertThat(nygVsWr.getRankPpr()).isEqualTo(1);
        assertThat(sfVsWr.getRankPpr()).isEqualTo(2);
    }

    @Test
    void runningTwiceUpdatesInPlaceRatherThanDuplicating() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Game game = saveGame("dvp-game-idempotent", ari, sf);
        Player wr = savePlayer("Repeat WR", "WR");
        savePlayerGameStats(wr, game, ari, new BigDecimal("12.00"), new BigDecimal("9.00"));

        defenseVsPositionStatsService.computeForWeek(SEASON, WEEK);
        long afterFirstRun = defenseVsPositionStatsRepository.count();

        defenseVsPositionStatsService.computeForWeek(SEASON, WEEK);
        long afterSecondRun = defenseVsPositionStatsRepository.count();

        assertThat(afterSecondRun).isEqualTo(afterFirstRun);
    }

    private Team team(String abbreviation) {
        return teamRepository.findByAbbreviation(abbreviation).orElseThrow();
    }

    private Player savePlayer(String fullName, String position) {
        return playerRepository.save(new Player(fullName, position, null, 1, "ACTIVE", LocalDate.of(1998, 1, 1)));
    }

    private Game saveGame(String externalRef, Team home, Team away) {
        return gameRepository.save(new Game(externalRef, SEASON, WEEK, home, away,
                Instant.parse("2099-09-07T17:00:00Z"), "Test Stadium", "FINAL"));
    }

    private void savePlayerGameStats(Player player, Game game, Team offenseTeam,
                                      BigDecimal fantasyPointsPpr, BigDecimal fantasyPointsStandard) {
        playerGameStatsRepository.save(new PlayerGameStats(player, game, offenseTeam,
                5, 4, 60, 0, 0, null, null, null, null, null, 1, fantasyPointsPpr, fantasyPointsStandard));
    }

    private DefenseVsPositionStats findRow(Team team, String position) {
        Optional<DefenseVsPositionStats> row = defenseVsPositionStatsRepository
                .findByTeamAndSeasonAndWeekAndPosition(team, SEASON, WEEK, position);
        assertThat(row).isPresent();
        return row.get();
    }
}
