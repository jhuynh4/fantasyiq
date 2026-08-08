package com.fantasyiq.ingestion.injuries;

import java.time.LocalDate;
import java.util.List;

public class StubInjuryProvider implements InjuryProvider {

    @Override
    public List<RawInjuryReport> fetchCurrentInjuries(String teamExternalId) {
        return List.of(
                // Matches StubStatsProvider's "Test Player" (ESPN id 12345) -- should resolve
                new RawInjuryReport("12345", "QUESTIONABLE", LocalDate.of(2026, 8, 1)),
                // No matching player has been ingested -- should be skipped, not fail the batch
                new RawInjuryReport("99999", "OUT", LocalDate.of(2026, 8, 1)));
    }
}
