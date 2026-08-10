package com.fantasyiq.ingestion.odds;

import java.util.List;

/**
 * No season/week parameter -- unlike ESPN, The Odds API has no concept of
 * "give me week N"; one call returns whatever games are currently on the
 * board (typically the upcoming week's slate). Matching a returned game to
 * one of ours happens on our side, in OddsIngestionService.
 */
public interface OddsProvider {

    List<RawGameOdds> fetchCurrentOdds();
}
