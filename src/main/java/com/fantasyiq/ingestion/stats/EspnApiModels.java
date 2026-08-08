package com.fantasyiq.ingestion.stats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

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
