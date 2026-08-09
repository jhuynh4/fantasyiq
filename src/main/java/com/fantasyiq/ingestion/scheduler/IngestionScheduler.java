package com.fantasyiq.ingestion.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron entry points for the four ingestion jobs. Kept separate from the
 * *IngestionService classes themselves (see package-info.java) -- those
 * stay independently triggerable via their controllers (manual runs,
 * backfills), while this owns only the "when does each one run
 * automatically" decision. Correlation id / ingestion_runs bookkeeping
 * happens inside IngestionRunService, not here, so it applies the same way
 * whether a run was triggered by this scheduler or a controller.
 */
@Component
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final PlayerIngestionService playerIngestionService;
    private final GameIngestionService gameIngestionService;
    private final InjuryIngestionService injuryIngestionService;
    private final GameStatsIngestionService gameStatsIngestionService;

    public IngestionScheduler(PlayerIngestionService playerIngestionService,
                               GameIngestionService gameIngestionService,
                               InjuryIngestionService injuryIngestionService,
                               GameStatsIngestionService gameStatsIngestionService) {
        this.playerIngestionService = playerIngestionService;
        this.gameIngestionService = gameIngestionService;
        this.injuryIngestionService = injuryIngestionService;
        this.gameStatsIngestionService = gameStatsIngestionService;
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

    @Scheduled(cron = "0 0 3 * * MON")
    public void scheduledGameStatsIngestion() {
        runSafely(() -> {
            GameStatsIngestionService.IngestGameStatsResult result =
                    gameStatsIngestionService.ingestGameStats(NflSeason.current());
            log.info("Scheduled game-stats ingestion complete: {} stat lines", result.statLinesIngested());
        });
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
