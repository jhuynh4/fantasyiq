package com.fantasyiq.api.dto;

public record GameStatsIngestResponse(int playersConsidered, int playersWithEspnId, int rawStatLinesFetched,
                                       int statLinesIngested) {
}
