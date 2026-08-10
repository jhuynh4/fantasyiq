package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.domain.stats.DefenseVsPositionStatsService;
import org.springframework.stereotype.Service;

/**
 * Not an ingestion job -- computeForWeek makes no external calls, it's pure
 * aggregation over already-ingested data (see DefenseVsPositionStatsService).
 * Wrapped in the same ingestion_runs/correlation-id infrastructure anyway,
 * since "did this week's defense stats get computed" deserves the same
 * operational visibility as the ingestion jobs it depends on.
 */
@Service
public class DefenseVsPositionStatsComputationService {

    private static final String SOURCE = "COMPUTED_DEFENSE_VS_POSITION";

    private final DefenseVsPositionStatsService defenseVsPositionStatsService;
    private final IngestionRunService ingestionRunService;

    public DefenseVsPositionStatsComputationService(DefenseVsPositionStatsService defenseVsPositionStatsService,
                                                      IngestionRunService ingestionRunService) {
        this.defenseVsPositionStatsService = defenseVsPositionStatsService;
        this.ingestionRunService = ingestionRunService;
    }

    public int computeForWeek(int season, int week) {
        return ingestionRunService.track(SOURCE, Integer::intValue,
                () -> defenseVsPositionStatsService.computeForWeek(season, week));
    }
}
