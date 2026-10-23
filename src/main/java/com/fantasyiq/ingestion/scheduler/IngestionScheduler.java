package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.domain.game.CurrentWeekResolver;
import com.fantasyiq.ingestion.odds.OddsApiProperties;
import com.fantasyiq.ingestion.weather.OpenWeatherProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Cron entry points for the ingestion jobs, plus the daily start/sit
 * scoring run that depends on them. Kept separate from the
 * *IngestionService classes themselves (see package-info.java) -- those
 * stay independently triggerable via their controllers (manual runs,
 * backfills), while this owns only the "when does each one run
 * automatically" decision. Correlation id / ingestion_runs bookkeeping
 * happens inside IngestionRunService, not here, so it applies the same way
 * whether a run was triggered by this scheduler or a controller.
 *
 * Times are the server's zone (UTC on ECS). The scoring run is last in the
 * morning chain -- players 06:00, games 06:15, injuries 07:00, odds 07:10,
 * weather 07:15, scoring 07:30 -- so it reads data the earlier jobs have just
 * refreshed. Odds and weather are skipped (with a log line, not a failed run)
 * when their API key is not configured, since a call without one can only fail.
 * Odds run once a day: the free tier allows 500 requests a month.
 *
 * Assumes a single app instance: with more than one, every instance would run
 * each job at the same time.
 */
@Component
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final PlayerIngestionService playerIngestionService;
    private final GameIngestionService gameIngestionService;
    private final InjuryIngestionService injuryIngestionService;
    private final GameStatsIngestionService gameStatsIngestionService;
    private final OddsIngestionService oddsIngestionService;
    private final WeatherIngestionService weatherIngestionService;
    private final OddsApiProperties oddsApiProperties;
    private final OpenWeatherProperties openWeatherProperties;
    private final CurrentWeekResolver currentWeekResolver;
    private final StartSitRecommendationComputationService startSitRecommendationComputationService;

    public IngestionScheduler(PlayerIngestionService playerIngestionService,
                               GameIngestionService gameIngestionService,
                               InjuryIngestionService injuryIngestionService,
                               GameStatsIngestionService gameStatsIngestionService,
                               OddsIngestionService oddsIngestionService,
                               WeatherIngestionService weatherIngestionService,
                               OddsApiProperties oddsApiProperties,
                               OpenWeatherProperties openWeatherProperties,
                               CurrentWeekResolver currentWeekResolver,
                               StartSitRecommendationComputationService startSitRecommendationComputationService) {
        this.playerIngestionService = playerIngestionService;
        this.gameIngestionService = gameIngestionService;
        this.injuryIngestionService = injuryIngestionService;
        this.gameStatsIngestionService = gameStatsIngestionService;
        this.oddsIngestionService = oddsIngestionService;
        this.weatherIngestionService = weatherIngestionService;
        this.oddsApiProperties = oddsApiProperties;
        this.openWeatherProperties = openWeatherProperties;
        this.currentWeekResolver = currentWeekResolver;
        this.startSitRecommendationComputationService = startSitRecommendationComputationService;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void scheduledPlayerIngestion() {
        runSafely(() -> {
            int count = playerIngestionService.ingestRosters();
            log.info("Scheduled player ingestion complete: {} players", count);
        });
    }

    @Scheduled(cron = "0 15 6 * * *")
    public void scheduledGameIngestion() {
        runSafely(() -> {
            int count = gameIngestionService.ingestSchedules(NflSeason.current());
            log.info("Scheduled game ingestion complete: {} games", count);
        });
    }

    @Scheduled(cron = "0 0 7 * * *")
    public void scheduledInjuryIngestion() {
        runSafely(() -> {
            int count = injuryIngestionService.ingestInjuries();
            log.info("Scheduled injury ingestion complete: {} reports", count);
        });
    }

    @Scheduled(cron = "0 10 7 * * *")
    public void scheduledOddsIngestion() {
        if (isBlank(oddsApiProperties.apiKey())) {
            log.info("No Odds API key configured; skipping scheduled odds ingestion");
            return;
        }
        runSafely(() -> {
            int count = oddsIngestionService.ingestOdds();
            log.info("Scheduled odds ingestion complete: {} betting lines", count);
        });
    }

    /** Forecasts for the current week's outdoor games (the service itself skips anything outside the forecast window). */
    @Scheduled(cron = "0 15 7 * * *")
    public void scheduledWeatherIngestion() {
        if (isBlank(openWeatherProperties.apiKey())) {
            log.info("No OpenWeatherMap key configured; skipping scheduled weather ingestion");
            return;
        }
        runSafely(() -> {
            Optional<CurrentWeekResolver.SeasonWeek> week = currentWeekResolver.resolve(Instant.now());
            if (week.isEmpty()) {
                log.info("No game week in range; skipping scheduled weather ingestion");
                return;
            }
            int count = weatherIngestionService.ingestForecasts(week.get().season(), week.get().week());
            log.info("Scheduled weather ingestion complete: season {} week {}, {} forecasts",
                    week.get().season(), week.get().week(), count);
        });
    }

    @Scheduled(cron = "0 0 3 * * MON")
    public void scheduledGameStatsIngestion() {
        runSafely(() -> {
            GameStatsIngestionService.IngestGameStatsResult result =
                    gameStatsIngestionService.ingestGameStats(NflSeason.current());
            log.info("Scheduled game-stats ingestion complete: {} stat lines", result.statLinesIngested());
        });
    }

    /**
     * Scores whichever week CurrentWeekResolver says is current (and refreshes
     * its cache entry, as computeForWeek always does). Does nothing outside the
     * season -- no game within range means there's no week worth scoring.
     */
    @Scheduled(cron = "0 30 7 * * *")
    public void scheduledRecommendationGeneration() {
        runSafely(() -> {
            Optional<CurrentWeekResolver.SeasonWeek> week = currentWeekResolver.resolve(Instant.now());
            if (week.isEmpty()) {
                log.info("No game week in range; skipping scheduled recommendation generation");
                return;
            }
            int count = startSitRecommendationComputationService.computeForWeek(
                    week.get().season(), week.get().week());
            log.info("Scheduled recommendation generation complete: season {} week {}, {} recommendations",
                    week.get().season(), week.get().week(), count);
        });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * IngestionRunService already records the failure in ingestion_runs and
     * rethrows -- this is the last line of defense so one failed run can't
     * take down the scheduler thread or otherwise disrupt the next job.
     */
    private void runSafely(Runnable job) {
        try {
            job.run();
        } catch (Exception e) {
            log.error("Scheduled ingestion run failed", e);
        }
    }
}
