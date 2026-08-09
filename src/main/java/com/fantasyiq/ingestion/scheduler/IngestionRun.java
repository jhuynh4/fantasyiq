package com.fantasyiq.ingestion.scheduler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_runs")
public class IngestionRun {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "records_processed")
    private Integer recordsProcessed;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    protected IngestionRun() {
        // JPA
    }

    public IngestionRun(String source, String correlationId) {
        this.source = source;
        this.correlationId = correlationId;
        this.startedAt = Instant.now();
        this.status = "RUNNING";
    }

    public void markSuccess(int recordsProcessed) {
        this.status = "SUCCESS";
        this.recordsProcessed = recordsProcessed;
        this.finishedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRecordsProcessed() {
        return recordsProcessed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
