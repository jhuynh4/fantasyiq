package com.fantasyiq.ingestion.stats;

import java.util.List;

/**
 * Adapter interface for a roster/schedule/box-score data vendor.
 */
public interface StatsProvider {

    List<RawTeam> fetchTeams();

    List<RawAthlete> fetchRoster(String teamExternalId);

    List<RawGame> fetchSchedule(String teamExternalId, int season);

    List<RawGameStats> fetchGameStats(String athleteExternalId, int season);
}
