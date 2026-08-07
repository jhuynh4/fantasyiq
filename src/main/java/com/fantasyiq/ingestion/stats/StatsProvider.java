package com.fantasyiq.ingestion.stats;

import java.util.List;

/**
 * Adapter interface for a roster/schedule data vendor. Box-score methods
 * (fetchGameStats) land later in Phase 2 once player_game_stats ingestion
 * is actually built.
 */
public interface StatsProvider {

    List<RawTeam> fetchTeams();

    List<RawAthlete> fetchRoster(String teamExternalId);

    List<RawGame> fetchSchedule(String teamExternalId);
}
