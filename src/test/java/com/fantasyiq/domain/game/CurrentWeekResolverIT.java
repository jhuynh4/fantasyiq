package com.fantasyiq.domain.game;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The unit test fixes the resolver's rules with a mocked repository; this
 * proves the derived query itself (earliest kickoff at or after a cutoff)
 * behaves that way against a real database.
 */
class CurrentWeekResolverIT extends IntegrationTestBase {

    private static final int SEASON = 2097;

    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private CurrentWeekResolver resolver;

    @Test
    void picksTheWeekOfTheEarliestUpcomingGameIgnoringPastWeeks() {
        Instant now = Instant.now();
        saveGame("past-wk7", 7, now.minus(Duration.ofDays(3)));
        saveGame("later-wk8", 8, now.plus(Duration.ofDays(2)));
        saveGame("sooner-wk8", 8, now.plus(Duration.ofDays(1)));

        assertThat(resolver.resolve(now)).contains(new CurrentWeekResolver.SeasonWeek(SEASON, 8));
    }

    @Test
    void aGameStillBeingPlayedKeepsItsWeekCurrent() {
        Instant now = Instant.now();
        saveGame("in-progress-wk7", 7, now.minus(Duration.ofHours(2)));
        saveGame("next-wk8", 8, now.plus(Duration.ofDays(3)));

        assertThat(resolver.resolve(now)).contains(new CurrentWeekResolver.SeasonWeek(SEASON, 7));
    }

    @Test
    void returnsNothingWhenTheOnlyUpcomingGameIsFarOff() {
        Instant now = Instant.now();
        saveGame("summer-wk1", 1, now.plus(Duration.ofDays(60)));

        assertThat(resolver.resolve(now)).isEmpty();
    }

    private void saveGame(String externalRef, int week, Instant kickoff) {
        Team home = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Team away = teamRepository.findByAbbreviation("ATL").orElseThrow();
        gameRepository.save(new Game(externalRef, SEASON, week, home, away, kickoff, null, "SCHEDULED"));
    }
}
