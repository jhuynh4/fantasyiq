package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.stats.WeatherForecastReconciliationService;
import com.fantasyiq.domain.team.StadiumLocation;
import com.fantasyiq.domain.team.StadiumLocationRepository;
import com.fantasyiq.ingestion.weather.RawWeatherForecast;
import com.fantasyiq.ingestion.weather.WeatherProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Only fetches a forecast for games that are (a) at an outdoor stadium and
 * (b) within OpenWeatherMap's ~5-day forecast window, not already past kickoff
 * -- calling any earlier just burns quota on a forecast that will change
 * before kickoff, and OpenWeatherMap has no historical data to return for a
 * game that's already been played. Games at a dome/fixed-roof stadium
 * (stadium_locations.is_dome) never need weather at all. Along the way this
 * backfills games.is_dome from the same stadium_locations lookup, since that
 * column exists specifically to be resolved once weather ingestion (and its
 * stadium data) exists.
 */
@Service
public class WeatherIngestionService {

    private static final String SOURCE = "WEATHER_PROVIDER";
    private static final Duration FORECAST_WINDOW = Duration.ofDays(5);

    private final GameRepository gameRepository;
    private final StadiumLocationRepository stadiumLocationRepository;
    private final WeatherProvider weatherProvider;
    private final WeatherForecastReconciliationService weatherForecastReconciliationService;
    private final IngestionRunService ingestionRunService;

    public WeatherIngestionService(GameRepository gameRepository,
                                    StadiumLocationRepository stadiumLocationRepository,
                                    WeatherProvider weatherProvider,
                                    WeatherForecastReconciliationService weatherForecastReconciliationService,
                                    IngestionRunService ingestionRunService) {
        this.gameRepository = gameRepository;
        this.stadiumLocationRepository = stadiumLocationRepository;
        this.weatherProvider = weatherProvider;
        this.weatherForecastReconciliationService = weatherForecastReconciliationService;
        this.ingestionRunService = ingestionRunService;
    }

    public int ingestForecasts(int season, int week) {
        return ingestionRunService.track(SOURCE, Integer::intValue, () -> doIngestForecasts(season, week));
    }

    private int doIngestForecasts(int season, int week) {
        List<Game> games = gameRepository.findBySeasonAndWeek(season, week);
        Instant now = Instant.now();
        Instant forecastCutoff = now.plus(FORECAST_WINDOW);

        int forecastsIngested = 0;
        for (Game game : games) {
            Optional<StadiumLocation> stadium = stadiumLocationRepository.findByTeam(game.getHomeTeam());
            if (stadium.isEmpty()) {
                continue;
            }

            StadiumLocation homeStadium = stadium.get();
            game.markDomeStatus(homeStadium.isDome());
            gameRepository.save(game);

            // OpenWeatherMap is a forecast API, not a historical one -- a
            // kickoff that's already passed has no meaningful "forecast" to
            // fetch, so skip it the same as one too far in the future.
            boolean kickoffAlreadyPassed = game.getKickoff().isBefore(now);
            boolean kickoffTooFarOut = game.getKickoff().isAfter(forecastCutoff);
            if (homeStadium.isDome() || kickoffAlreadyPassed || kickoffTooFarOut) {
                continue;
            }

            Optional<RawWeatherForecast> forecast = weatherProvider.fetchForecast(
                    homeStadium.getLatitude().doubleValue(), homeStadium.getLongitude().doubleValue(), game.getKickoff());

            if (forecast.isEmpty()) {
                continue;
            }

            weatherForecastReconciliationService.resolveOrCreate(game, forecast.get().temperatureF(),
                    forecast.get().windMph(), forecast.get().precipitationPct(), forecast.get().conditions());
            forecastsIngested++;
        }
        return forecastsIngested;
    }
}
