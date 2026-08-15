package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.analytics.backtest.BacktestResult;
import com.fantasyiq.analytics.backtest.BacktestService;
import org.springframework.stereotype.Service;

/**
 * Not an ingestion job -- runBacktest makes no external calls, it's pure
 * composition/analysis over already-ingested/computed data (see
 * BacktestService). Wrapped in the same ingestion_runs/correlation-id
 * infrastructure anyway, same reasoning as StartSitRecommendationComputationService.
 */
@Service
public class BacktestComputationService {

    private static final String SOURCE = "COMPUTED_BACKTEST";

    private final BacktestService backtestService;
    private final IngestionRunService ingestionRunService;

    public BacktestComputationService(BacktestService backtestService, IngestionRunService ingestionRunService) {
        this.backtestService = backtestService;
        this.ingestionRunService = ingestionRunService;
    }

    public BacktestResult runBacktest(int season) {
        return ingestionRunService.track(SOURCE, BacktestResult::matchedWithActualStats,
                () -> backtestService.runBacktest(season));
    }
}
