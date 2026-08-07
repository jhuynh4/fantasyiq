package com.fantasyiq.ingestion.stats;

import java.util.List;

/**
 * Adapter interface for a roster/player data vendor. Scoped to what Phase 1
 * needs (teams + rosters); schedule and box-score methods land in Phase 2
 * once games/player_game_stats ingestion is actually built.
 */
public interface StatsProvider {

    List<RawTeam> fetchTeams();

    List<RawAthlete> fetchRoster(String teamExternalId);
}
