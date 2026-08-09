package com.fantasyiq.ingestion.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, UUID> {
}
