package com.fantasyiq.ingestion.stats;

import java.time.LocalDate;
import java.util.List;

/**
 * Fixed-data test double so ingestion tests never hit the real ESPN API.
 * WireMock-based contract tests against captured payloads are a Phase 2 item;
 * this is sufficient for Phase 1's upsert-idempotency test.
 */
public class StubStatsProvider implements StatsProvider {

    @Override
    public List<RawTeam> fetchTeams() {
        return List.of(new RawTeam("999", "ARI", "Arizona Cardinals"));
    }

    @Override
    public List<RawAthlete> fetchRoster(String teamExternalId) {
        return List.of(new RawAthlete("12345", "Test Player", "WR", 11, "ACTIVE", LocalDate.of(1998, 1, 1)));
    }
}
