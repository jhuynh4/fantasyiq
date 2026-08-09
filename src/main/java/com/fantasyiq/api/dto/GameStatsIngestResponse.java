package com.fantasyiq.api.dto;

import java.util.Set;

public record GameStatsIngestResponse(int playersConsidered, int playersWithEspnId, int rawStatLinesFetched,
                                       int statLinesIngested, Set<String> sampleUnmatchedEventIds,
                                       Set<String> sampleStoredExternalRefs) {
}
