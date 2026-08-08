package com.fantasyiq.ingestion.injuries;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Minimal mirror of ESPN's roster response, scoped to just the injuries
 * field per athlete. Deliberately duplicated from ingestion.stats rather
 * than shared -- this package is a separate, independently-swappable
 * adapter (InjuryProvider), even though it happens to read the same ESPN
 * endpoint as StatsProvider.fetchRoster today. See
 * docs/data-source-integration.md section 1 for why.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EspnInjuryRosterResponse(List<EspnInjuryPositionGroup> athletes) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnInjuryPositionGroup(List<EspnInjuryAthlete> items) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnInjuryAthlete(String id, List<EspnInjuryEntry> injuries) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnInjuryEntry(String status, String date) {
}
