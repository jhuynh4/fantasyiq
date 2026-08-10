package com.fantasyiq.ingestion.stats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Minimal mirrors of ESPN's actual JSON shapes (verified against live responses
 * from site.api.espn.com), covering only the fields we consume. ESPN's real
 * payloads carry dozens of irrelevant fields (logos, links, contracts, ...) --
 * @JsonIgnoreProperties(ignoreUnknown = true) lets us ignore all of that instead
 * of modeling it. Package-private: these are wire-format details EspnStatsProvider
 * and EspnResponseMapper own; nothing outside this package should touch them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EspnTeamsResponse(List<EspnSport> sports) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnSport(List<EspnLeague> leagues) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnLeague(List<EspnTeamWrapper> teams) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnTeamWrapper(EspnTeam team) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnTeam(String id, String abbreviation, String displayName) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnRosterResponse(List<EspnPositionGroup> athletes) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnPositionGroup(List<EspnAthlete> items) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnAthlete(String id, String fullName, String jersey, EspnPosition position,
                    EspnStatus status, String dateOfBirth) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnPosition(String abbreviation) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnStatus(String type) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnScheduleResponse(List<EspnEvent> events) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnEvent(String id, String date, EspnEventSeason season, EspnSeasonType seasonType,
                  EspnWeek week, List<EspnCompetition> competitions) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnEventSeason(Integer year) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnSeasonType(Integer type, String name) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnWeek(Integer number) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnCompetition(EspnVenue venue, List<EspnCompetitor> competitors, EspnCompetitionStatus status) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnVenue(String fullName) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnCompetitor(String homeAway, EspnTeam team) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnCompetitionStatus(EspnStatusType type) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnStatusType(String state) {
}

/**
 * ESPN's gamelog response is column-oriented, not field-oriented like
 * everything else: top-level "names" defines what each positional slot in
 * an event's "stats" array means, and the SET of names varies by position
 * (a QB's names differ entirely from a WR's). The actual per-game stat
 * lines are nested under seasonTypes[].categories[].events[] -- NOT the
 * top-level "events" map, which only carries descriptive metadata
 * (week/opponent/date, keyed by event id) with no stats at all; easy to
 * miss on a first read. That metadata map IS where the athlete's team for
 * that specific game lives though (events[id].team.id) -- used to populate
 * player_game_stats.team_id, since a player's roster team can change
 * mid-season (trades) and their *current* team would be wrong for old games.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EspnGameLogResponse(List<String> names, List<EspnGameLogSeasonType> seasonTypes,
                            Map<String, EspnGameLogEventMeta> events) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnGameLogEventMeta(EspnTeam team) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnGameLogSeasonType(List<EspnGameLogCategory> categories) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnGameLogCategory(List<EspnGameLogEvent> events) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record EspnGameLogEvent(String eventId, List<String> stats) {
}
