package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.domain.game.CurrentWeekResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers only what the scheduler itself decides for scoring -- which week to
 * pass, when to skip, and that a failure can't escape and kill the scheduler
 * thread. The cron expressions themselves aren't asserted here.
 */
@ExtendWith(MockitoExtension.class)
class IngestionSchedulerTest {

    @Mock
    private PlayerIngestionService playerIngestionService;
    @Mock
    private GameIngestionService gameIngestionService;
    @Mock
    private InjuryIngestionService injuryIngestionService;
    @Mock
    private GameStatsIngestionService gameStatsIngestionService;
    @Mock
    private CurrentWeekResolver currentWeekResolver;
    @Mock
    private StartSitRecommendationComputationService computationService;

    private IngestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new IngestionScheduler(playerIngestionService, gameIngestionService, injuryIngestionService,
                gameStatsIngestionService, currentWeekResolver, computationService);
    }

    @Test
    void scoresTheWeekTheResolverReports() {
        when(currentWeekResolver.resolve(any()))
                .thenReturn(Optional.of(new CurrentWeekResolver.SeasonWeek(2026, 7)));
        when(computationService.computeForWeek(2026, 7)).thenReturn(812);

        scheduler.scheduledRecommendationGeneration();

        verify(computationService).computeForWeek(2026, 7);
    }

    @Test
    void doesNothingWhenNoWeekIsInRange() {
        when(currentWeekResolver.resolve(any())).thenReturn(Optional.empty());

        scheduler.scheduledRecommendationGeneration();

        verify(computationService, never()).computeForWeek(anyInt(), anyInt());
    }

    @Test
    void aFailedRunDoesNotEscapeTheScheduler() {
        when(currentWeekResolver.resolve(any()))
                .thenReturn(Optional.of(new CurrentWeekResolver.SeasonWeek(2026, 7)));
        when(computationService.computeForWeek(2026, 7)).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> scheduler.scheduledRecommendationGeneration()).doesNotThrowAnyException();
    }
}
