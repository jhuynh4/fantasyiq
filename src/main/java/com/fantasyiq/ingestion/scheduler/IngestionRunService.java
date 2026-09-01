package com.fantasyiq.ingestion.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Wraps an ingestion job with ingestion_runs bookkeeping: a RUNNING row is
 * written before the job starts, then updated to SUCCESS (with the record
 * count) or FAILED (with the exception message) once it finishes. The
 * exception is always rethrown -- this only records failures, it never
 * swallows them. recordCountExtractor exists because each job returns a
 * different result shape (a plain int, or a richer record like
 * GameStatsIngestionService.IngestGameStatsResult).
 *
 * Also owns the correlation id lifecycle: one id per run, put in MDC for
 * the duration of the job (so every structured log line the job produces
 * carries it -- see logback-spring.xml) and stored on the IngestionRun row
 * itself, so a run found in the audit table can be traced straight back to
 * its exact logs. This applies uniformly whether the job was triggered
 * manually (via a controller) or by IngestionScheduler.
 *
 * Also the single choke point every job flows through, which makes it the
 * natural place to emit a live `ingestion.run.duration` Micrometer timer
 * (tagged by source + outcome) alongside the ingestion_runs audit row --
 * one instrumented, rather than duplicating a Timer.start/stop pair inside
 * every individual *IngestionService.
 */
@Service
public class IngestionRunService {

    private static final String MDC_KEY = "correlationId";
    private static final String METRIC_NAME = "ingestion.run.duration";

    private final IngestionRunRepository ingestionRunRepository;
    private final MeterRegistry meterRegistry;

    public IngestionRunService(IngestionRunRepository ingestionRunRepository, MeterRegistry meterRegistry) {
        this.ingestionRunRepository = ingestionRunRepository;
        this.meterRegistry = meterRegistry;
    }

    public <T> T track(String source, ToIntFunction<T> recordCountExtractor, Supplier<T> job) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, correlationId);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            IngestionRun run = ingestionRunRepository.save(new IngestionRun(source, correlationId));
            try {
                T result = job.get();
                run.markSuccess(recordCountExtractor.applyAsInt(result));
                ingestionRunRepository.save(run);
                recordDuration(sample, source, "success");
                return result;
            } catch (RuntimeException e) {
                run.markFailed(e.getMessage());
                ingestionRunRepository.save(run);
                recordDuration(sample, source, "failure");
                throw e;
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private void recordDuration(Timer.Sample sample, String source, String outcome) {
        sample.stop(Timer.builder(METRIC_NAME)
                .tag("source", source)
                .tag("outcome", outcome)
                .register(meterRegistry));
    }
}
