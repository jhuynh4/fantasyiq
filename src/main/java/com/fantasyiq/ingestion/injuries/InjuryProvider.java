package com.fantasyiq.ingestion.injuries;

import java.util.List;

/**
 * Scoped per-team, matching StatsProvider's shape, so InjuryIngestionService
 * follows the same "resolve teams, loop, fetch" orchestration pattern as
 * player and game ingestion.
 */
public interface InjuryProvider {

    List<RawInjuryReport> fetchCurrentInjuries(String teamExternalId);
}
