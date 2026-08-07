package com.fantasyiq.ingestion.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Fixed-data test double so ingestion tests never hit the real ESPN API.
 * WireMock-based contract tests against captured payloads are a Phase 2 item;
 * this is sufficient for Phase 1/2's upsert-idempotency tests.
 */
public class StubStatsProvider implements StatsProvider {

    @Override
    public List<RawTeam> fetchTeams() {
        return List.of(
                new RawTeam("999", "ARI", "Arizona Cardinals"),
                new RawTeam("998", "SF", "San Francisco 49ers"));
    }

    @Override
    public List<RawAthlete> fetchRoster(String teamExternalId) {
        // Only "999" (ARI) has a roster -- keeps PlayerIngestionServiceIT's
        // "exactly 1 player" assertions unaffected by the second team added
        // here for the schedule/game tests.
        if (!"999".equals(teamExternalId)) {
            return List.of();
        }
        return List.of(new RawAthlete("12345", "Test Player", "WR", 11, "ACTIVE", LocalDate.of(1998, 1, 1)));
    }

    @Override
    public List<RawGame> fetchSchedule(String teamExternalId) {
        // Same game returned for both teams' schedule fetch, exactly like ESPN's
        // real behavior -- exercises the dedup-by-external_ref path.
        return List.of(new RawGame(
                "555", 2026, 1, "999", "998",
                Instant.parse("2026-09-07T17:00:00Z"), "State Farm Stadium", "SCHEDULED"));
    }
}
