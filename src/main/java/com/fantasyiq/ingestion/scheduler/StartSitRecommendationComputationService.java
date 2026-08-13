package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.analytics.startsit.StartSitRecommendationService;
import org.springframework.stereotype.Service;

/**
 * Not an ingestion job -- computeForWeek makes no external calls, it's pure
 * composition over already-ingested/computed data (see
 * StartSitRecommendationService). Wrapped in the same ingestion_runs/
 * correlation-id infrastructure anyway, same reasoning as
 * DefenseVsPositionStatsComputationService.
 */
@Service
public class StartSitRecommendationComputationService {

    private static final String SOURCE = "COMPUTED_START_SIT";

    private final StartSitRecommendationService startSitRecommendationService;
    private final IngestionRunService ingestionRunService;

    public StartSitRecommendationComputationService(StartSitRecommendationService startSitRecommendationService,
                                                      IngestionRunService ingestionRunService) {
        this.startSitRecommendationService = startSitRecommendationService;
        this.ingestionRunService = ingestionRunService;
    }

    public int computeForWeek(int season, int week) {
        return ingestionRunService.track(SOURCE, Integer::intValue,
                () -> startSitRecommendationService.computeForWeek(season, week));
    }
}
