package com.fantasyiq.ingestion.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
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
    void everyLogLineFromARunCarriesItsCorrelationIdAndMatchesTheAuditRow() {
        List<ILoggingEvent> events = captureRunLogs(() ->
                ingestionRunService.track("TEST_SOURCE", Integer::intValue, () -> 5));

        ArgumentCaptor<IngestionRun> saved = ArgumentCaptor.forClass(IngestionRun.class);
        verify(ingestionRunRepository, atLeastOnce()).save(saved.capture());
        String auditCorrelationId = saved.getAllValues().get(0).getCorrelationId();

        assertThat(events).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.startsWith("Ingestion run started"))
                .anyMatch(m -> m.startsWith("Ingestion run succeeded") && m.contains("records=5"));
        assertThat(events).allSatisfy(event ->
                assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", auditCorrelationId));
    }

    @Test
    void aFailedRunLogsItsErrorAtErrorLevelWithTheCorrelationId() {
        List<ILoggingEvent> events = captureRunLogs(() ->
                assertThatThrownBy(() -> ingestionRunService.track("TEST_SOURCE", Integer::intValue, () -> {
                    throw new RuntimeException("boom");
                })).hasMessage("boom"));

        ILoggingEvent failure = events.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .findFirst().orElseThrow();
        assertThat(failure.getFormattedMessage()).contains("Ingestion run failed").contains("boom");
        assertThat(failure.getMDCPropertyMap()).containsKey("correlationId");
    }

    @Test
    void aFailedRunLogsTheUnderlyingCauseNotJustTheWrapperMessage() {
        RuntimeException wrapped = new RuntimeException("ESPN teams endpoint unavailable",
                new IllegalStateException("HTTP 403 Forbidden from site.api.espn.com"));

        List<ILoggingEvent> events = captureRunLogs(() ->
                assertThatThrownBy(() -> ingestionRunService.track("TEST_SOURCE", Integer::intValue, () -> {
                    throw wrapped;
                })).isSameAs(wrapped));

        ILoggingEvent failure = events.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(failure.getFormattedMessage()).contains("rootCause=java.lang.IllegalStateException: HTTP 403 Forbidden");
        assertThat(failure.getThrowableProxy()).isNotNull();
        assertThat(failure.getThrowableProxy().getCause().getMessage()).contains("HTTP 403 Forbidden");
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

    private List<ILoggingEvent> captureRunLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(IngestionRunService.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
        return appender.list;
    }
}
