package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.stats.BettingLine;
import com.fantasyiq.domain.stats.BettingLineRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import com.fantasyiq.ingestion.odds.OddsProvider;
import com.fantasyiq.ingestion.odds.StubOddsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StubOddsProvider returns odds for "Arizona Cardinals" @ "San Francisco 49ers"
 * (matches a real seeded game fixture here) and "Dallas Cowboys" @ "New York
 * Giants" (deliberately no matching game -- should be skipped, not fail the
 * batch), same pattern as InjuryIngestionServiceIT's known/unknown split.
 */
class OddsIngestionServiceIT extends IntegrationTestBase {

    private static final int SEASON = 2099;
    private static final int WEEK = 1;

    @TestConfiguration
    static class StubProviderConfig {
        @Bean
        @Primary
        OddsProvider oddsProvider() {
            return new StubOddsProvider();
        }
    }

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private BettingLineRepository bettingLineRepository;
    @Autowired
    private OddsIngestionService oddsIngestionService;

    @BeforeEach
    void cleanUp() {
        bettingLineRepository.deleteAll();
        gameRepository.deleteAll();
    }

    @Test
    void skipsUnmatchedGamesAndIngestsBothSidesOfKnownOnes() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Game game = saveGame("odds-game-1", ari, sf, Instant.parse("2099-09-07T17:00:00Z"));

        int bettingLinesIngested = oddsIngestionService.ingestOdds();

        // 2 rows (home + away) for the matched game; the Dallas/Giants entry has no matching game
        assertThat(bettingLinesIngested).isEqualTo(2);
        assertThat(bettingLineRepository.count()).isEqualTo(2);

        Optional<BettingLine> homeLine = bettingLineRepository.findByGameAndTeam(game, ari);
        assertThat(homeLine).isPresent();
        assertThat(homeLine.get().getSpread()).isEqualByComparingTo("-3.5");
        // (45.5 - (-3.5)) / 2 = 24.50
        assertThat(homeLine.get().getImpliedTeamTotal()).isEqualByComparingTo("24.50");

        Optional<BettingLine> awayLine = bettingLineRepository.findByGameAndTeam(game, sf);
        assertThat(awayLine).isPresent();
        assertThat(awayLine.get().getSpread()).isEqualByComparingTo("3.5");
        // (45.5 - 3.5) / 2 = 21.00
        assertThat(awayLine.get().getImpliedTeamTotal()).isEqualByComparingTo("21.00");
    }

    @Test
    void runningTwiceUpdatesInPlaceRatherThanDuplicating() {
        Team ari = team("ARI");
        Team sf = team("SF");
        saveGame("odds-game-idempotent", ari, sf, Instant.parse("2099-09-07T17:00:00Z"));

        oddsIngestionService.ingestOdds();
        long afterFirstRun = bettingLineRepository.count();

        oddsIngestionService.ingestOdds();
        long afterSecondRun = bettingLineRepository.count();

        assertThat(afterSecondRun).isEqualTo(afterFirstRun);
    }

    private Team team(String abbreviation) {
        return teamRepository.findByAbbreviation(abbreviation).orElseThrow();
    }

    private Game saveGame(String externalRef, Team home, Team away, Instant kickoff) {
        return gameRepository.save(new Game(externalRef, SEASON, WEEK, home, away, kickoff, "Test Stadium", "SCHEDULED"));
    }
}
