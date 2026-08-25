package com.fantasyiq.ingestion.scheduler;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A real SimpleMeterRegistry, not mocked -- asserting on Mockito-verified
 * calls to a mocked MeterRegistry would only prove the right methods were
 * called, not that a real timer with the right name/tags/count ends up
 * queryable, which is the actual thing worth testing here.
 */
@ExtendWith(MockitoExtension.class)
class IngestionRunServiceTest {

    @Mock
    private IngestionRunRepository ingestionRunRepository;

    private SimpleMeterRegistry meterRegistry;
    private IngestionRunService ingestionRunService;

    @BeforeEach
    void setUp() {
        when(ingestionRunRepository.save(any(IngestionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        meterRegistry = new SimpleMeterRegistry();
        ingestionRunService = new IngestionRunService(ingestionRunRepository, meterRegistry);
    }

    @Test
    void recordsATimerTaggedSuccessWhenTheJobSucceeds() {
        int result = ingestionRunService.track("TEST_SOURCE", Integer::intValue, () -> 5);

        assertThat(result).isEqualTo(5);
        Timer timer = meterRegistry.get("ingestion.run.duration")
                .tag("source", "TEST_SOURCE").tag("outcome", "success").timer();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordsATimerTaggedFailureWhenTheJobThrows() {
        RuntimeException failure = new RuntimeException("boom");

        assertThatThrownBy(() -> ingestionRunService.track("TEST_SOURCE", Integer::intValue, () -> {
            throw failure;
        })).isSameAs(failure);

        Timer timer = meterRegistry.get("ingestion.run.duration")
                .tag("source", "TEST_SOURCE").tag("outcome", "failure").timer();
        assertThat(timer.count()).isEqualTo(1);
    }
}
