-- V7: ties each ingestion_runs row to the correlation id its structured
-- logs were tagged with (see IngestionRunService), so a failed/odd run
-- found in this table can be traced straight back to its exact log lines.
ALTER TABLE ingestion_runs ADD COLUMN correlation_id VARCHAR(36);
