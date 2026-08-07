package com.fantasyiq.ingestion.stats;

import java.time.Instant;

public record RawGame(String externalId, Integer season, Integer week, String homeTeamExternalId,
                       String awayTeamExternalId, Instant kickoff, String venue, String status) {
}
