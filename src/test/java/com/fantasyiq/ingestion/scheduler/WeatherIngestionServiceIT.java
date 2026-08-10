package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.stats.WeatherForecast;
import com.fantasyiq.domain.stats.WeatherForecastRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import com.fantasyiq.ingestion.weather.StubWeatherProvider;
import com.fantasyiq.ingestion.weather.WeatherProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses real seeded stadium_locations data (V10) rather than fixtures of its
 * own -- team/stadium is static reference data, not per-test state, same
 * reasoning as DefenseVsPositionStatsServiceIT reusing real seeded teams.
 * NO (Caesars Superdome) and GB (Lambeau Field, outdoor) are picked
 * specifically for their real, distinct is_dome values.
 */
class WeatherIngestionServiceIT extends IntegrationTestBase {

    private static final int SEASON = 2099;
    private static final int WEEK = 1;

    @TestConfiguration
    static class StubProviderConfig {
        @Bean
        @Primary
        WeatherProvider weatherProvider() {
            return new StubWeatherProvider();
        }
    }

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private WeatherForecastRepository weatherForecastRepository;
    @Autowired
    private WeatherIngestionService weatherIngestionService;

    @BeforeEach
    void cleanUp() {
        weatherForecastRepository.deleteAll();
        gameRepository.deleteAll();
    }

    @Test
    void skipsDomeStadiumGamesButStillBackfillsIsDome() {
        Team no = team("NO");
        Team tb = team("TB");
        Game game = saveGame("weather-dome-game", no, tb, Instant.now().plus(Duration.ofDays(2)));

        int forecastsIngested = weatherIngestionService.ingestForecasts(SEASON, WEEK);

        assertThat(forecastsIngested).isEqualTo(0);
        assertThat(weatherForecastRepository.findByGame(game)).isEmpty();
        assertThat(gameRepository.findById(game.getId()).orElseThrow().getIsDome()).isTrue();
    }

    @Test
    void ingestsForecastForOutdoorGameWithinTheForecastWindow() {
        Team gb = team("GB");
        Team chi = team("CHI");
        Game game = saveGame("weather-outdoor-game", gb, chi, Instant.now().plus(Duration.ofDays(2)));

        int forecastsIngested = weatherIngestionService.ingestForecasts(SEASON, WEEK);

        assertThat(forecastsIngested).isEqualTo(1);
        assertThat(gameRepository.findById(game.getId()).orElseThrow().getIsDome()).isFalse();

        Optional<WeatherForecast> forecast = weatherForecastRepository.findByGame(game);
        assertThat(forecast).isPresent();
        assertThat(forecast.get().getTemperatureF()).isEqualTo(72);
    }

    @Test
    void skipsOutdoorGamesOutsideTheForecastWindow() {
        Team gb = team("GB");
        Team chi = team("CHI");
        saveGame("weather-too-far-out-game", gb, chi, Instant.now().plus(Duration.ofDays(30)));

        int forecastsIngested = weatherIngestionService.ingestForecasts(SEASON, WEEK);

        assertThat(forecastsIngested).isEqualTo(0);
    }

    /**
     * OpenWeatherMap is a forecast API -- it has nothing meaningful to
     * return for a game that's already been played, so a past kickoff must
     * be skipped the same as one too far in the future rather than fetching
     * "today's weather" and mislabeling it as that game's forecast.
     */
    @Test
    void skipsOutdoorGamesWhoseKickoffAlreadyPassed() {
        Team gb = team("GB");
        Team chi = team("CHI");
        saveGame("weather-already-played-game", gb, chi, Instant.now().minus(Duration.ofDays(30)));

        int forecastsIngested = weatherIngestionService.ingestForecasts(SEASON, WEEK);

        assertThat(forecastsIngested).isEqualTo(0);
    }

    @Test
    void runningTwiceUpdatesInPlaceRatherThanDuplicating() {
        Team gb = team("GB");
        Team chi = team("CHI");
        saveGame("weather-idempotent-game", gb, chi, Instant.now().plus(Duration.ofDays(2)));

        weatherIngestionService.ingestForecasts(SEASON, WEEK);
        long afterFirstRun = weatherForecastRepository.count();

        weatherIngestionService.ingestForecasts(SEASON, WEEK);
        long afterSecondRun = weatherForecastRepository.count();

        assertThat(afterSecondRun).isEqualTo(afterFirstRun);
    }

    private Team team(String abbreviation) {
        return teamRepository.findByAbbreviation(abbreviation).orElseThrow();
    }

    private Game saveGame(String externalRef, Team home, Team away, Instant kickoff) {
        return gameRepository.save(new Game(externalRef, SEASON, WEEK, home, away, kickoff, "Test Stadium", "SCHEDULED"));
    }
}
