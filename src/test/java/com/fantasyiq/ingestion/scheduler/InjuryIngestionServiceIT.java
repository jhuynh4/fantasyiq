package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.stats.InjuryReportRepository;
import com.fantasyiq.ingestion.injuries.InjuryProvider;
import com.fantasyiq.ingestion.injuries.StubInjuryProvider;
import com.fantasyiq.ingestion.stats.StatsProvider;
import com.fantasyiq.ingestion.stats.StubStatsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

class InjuryIngestionServiceIT extends IntegrationTestBase {

    @TestConfiguration
    static class StubProvidersConfig {
        @Bean
        @Primary
        StatsProvider statsProvider() {
            return new StubStatsProvider();
        }

        @Bean
        @Primary
        InjuryProvider injuryProvider() {
            return new StubInjuryProvider();
        }
    }

    @Autowired
    private PlayerIngestionService playerIngestionService;
    @Autowired
    private InjuryIngestionService injuryIngestionService;
    @Autowired
    private InjuryReportRepository injuryReportRepository;

    @Test
    void skipsUnknownPlayersAndIngestsKnownOnes() {
        // Seeds "Test Player" / ESPN id 12345, matching StubInjuryProvider's known report
        playerIngestionService.ingestRosters();

        int reportsIngested = injuryIngestionService.ingestInjuries();

        // StubStatsProvider has 2 teams, and StubInjuryProvider ignores the
        // team id parameter -- so the known report is processed once per
        // team (2 total; the unknown one is skipped both times), but since
        // both processings resolve to the same player/date/source, the
        // reconciliation upsert collapses them into a single row.
        assertThat(reportsIngested).isEqualTo(2);
        assertThat(injuryReportRepository.count()).isEqualTo(1);
    }

    @Test
    void runningTwiceDoesNotDuplicateTheSameReport() {
        playerIngestionService.ingestRosters();

        injuryIngestionService.ingestInjuries();
        long afterFirstRun = injuryReportRepository.count();

        injuryIngestionService.ingestInjuries();
        long afterSecondRun = injuryReportRepository.count();

        assertThat(afterSecondRun).isEqualTo(afterFirstRun);
    }
}
