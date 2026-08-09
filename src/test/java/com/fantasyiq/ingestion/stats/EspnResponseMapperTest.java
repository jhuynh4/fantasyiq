package com.fantasyiq.ingestion.stats;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EspnResponseMapperTest {

    @Test
    void mapsNestedTeamsResponseToFlatRawTeams() {
        EspnTeamsResponse response = new EspnTeamsResponse(List.of(
                new EspnSport(List.of(
                        new EspnLeague(List.of(
                                new EspnTeamWrapper(new EspnTeam("22", "ARI", "Arizona Cardinals")),
                                new EspnTeamWrapper(new EspnTeam("25", "SF", "San Francisco 49ers"))
                        ))
                ))
        ));

        List<RawTeam> teams = EspnResponseMapper.toRawTeams(response);

        assertThat(teams).containsExactly(
                new RawTeam("22", "ARI", "Arizona Cardinals"),
                new RawTeam("25", "SF", "San Francisco 49ers"));
    }

    @Test
    void toRawTeamsToleratesMissingNesting() {
        assertThat(EspnResponseMapper.toRawTeams(null)).isEmpty();
        assertThat(EspnResponseMapper.toRawTeams(new EspnTeamsResponse(null))).isEmpty();
    }

    @Test
    void mapsAthleteGroupsToFlatRawAthletes() {
        EspnAthlete athlete = new EspnAthlete(
                "5084939", "Isaiah Adams", "74",
                new EspnPosition("G"), new EspnStatus("active"), "2000-07-21T07:00Z");
        EspnRosterResponse response = new EspnRosterResponse(List.of(
                new EspnPositionGroup(List.of(athlete))));

        List<RawAthlete> athletes = EspnResponseMapper.toRawAthletes(response);

        assertThat(athletes).containsExactly(new RawAthlete(
                "5084939", "Isaiah Adams", "G", 74, "ACTIVE", LocalDate.of(2000, 7, 21)));
    }

    @Test
    void toRawAthletesToleratesMissingNesting() {
        assertThat(EspnResponseMapper.toRawAthletes(null)).isEmpty();
        assertThat(EspnResponseMapper.toRawAthletes(new EspnRosterResponse(null))).isEmpty();
    }

    @Test
    void missingStatusDefaultsToActive() {
        EspnAthlete athlete = new EspnAthlete("1", "No Status Guy", "9", new EspnPosition("QB"), null, null);
        EspnRosterResponse response = new EspnRosterResponse(List.of(new EspnPositionGroup(List.of(athlete))));

        List<RawAthlete> athletes = EspnResponseMapper.toRawAthletes(response);

        assertThat(athletes).hasSize(1);
        assertThat(athletes.get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void parseJerseyNumberHandlesBlankAndNonNumeric() {
        assertThat(EspnResponseMapper.parseJerseyNumber("12")).isEqualTo(12);
        assertThat(EspnResponseMapper.parseJerseyNumber(null)).isNull();
        assertThat(EspnResponseMapper.parseJerseyNumber("")).isNull();
        assertThat(EspnResponseMapper.parseJerseyNumber("N/A")).isNull();
    }

    @Test
    void parseBirthDateExtractsDatePortionOfIsoTimestamp() {
        assertThat(EspnResponseMapper.parseBirthDate("2000-07-21T07:00Z")).isEqualTo(LocalDate.of(2000, 7, 21));
        assertThat(EspnResponseMapper.parseBirthDate(null)).isNull();
        assertThat(EspnResponseMapper.parseBirthDate("")).isNull();
    }

    @Test
    void mapsRegularSeasonEventToRawGame() {
        EspnEvent event = new EspnEvent(
                "401671000", "2026-09-07T17:00Z",
                new EspnEventSeason(2026), new EspnSeasonType(2, "Regular Season"), new EspnWeek(1),
                List.of(new EspnCompetition(
                        new EspnVenue("Arrowhead Stadium"),
                        List.of(
                                new EspnCompetitor("home", new EspnTeam("12", "KC", "Kansas City Chiefs")),
                                new EspnCompetitor("away", new EspnTeam("22", "ARI", "Arizona Cardinals"))
                        ),
                        new EspnCompetitionStatus(new EspnStatusType("pre")))));
        EspnScheduleResponse response = new EspnScheduleResponse(List.of(event));

        List<RawGame> games = EspnResponseMapper.toRawGames(response);

        assertThat(games).containsExactly(new RawGame(
                "401671000", 2026, 1, "12", "22",
                Instant.parse("2026-09-07T17:00:00Z"), "Arrowhead Stadium", "SCHEDULED"));
    }

    @Test
    void filtersOutNonRegularSeasonEvents() {
        // Real shape captured from ESPN's schedule endpoint in preseason: seasonType.type == 1
        EspnEvent preseasonEvent = new EspnEvent(
                "401873280", "2026-08-15T17:00Z",
                new EspnEventSeason(2026), new EspnSeasonType(1, "Preseason"), new EspnWeek(2),
                List.of(new EspnCompetition(
                        new EspnVenue("MetLife Stadium"),
                        List.of(
                                new EspnCompetitor("home", new EspnTeam("19", "NYG", "New York Giants")),
                                new EspnCompetitor("away", new EspnTeam("16", "MIN", "Minnesota Vikings"))
                        ),
                        new EspnCompetitionStatus(new EspnStatusType("pre")))));

        List<RawGame> games = EspnResponseMapper.toRawGames(new EspnScheduleResponse(List.of(preseasonEvent)));

        assertThat(games).isEmpty();
    }

    @Test
    void toRawGamesToleratesMissingNesting() {
        assertThat(EspnResponseMapper.toRawGames(null)).isEmpty();
        assertThat(EspnResponseMapper.toRawGames(new EspnScheduleResponse(null))).isEmpty();
    }

    @Test
    void mapsGameStatusStates() {
        assertThat(statusFor("pre")).isEqualTo("SCHEDULED");
        assertThat(statusFor("in")).isEqualTo("IN_PROGRESS");
        assertThat(statusFor("post")).isEqualTo("FINAL");
        assertThat(statusFor(null)).isEqualTo("SCHEDULED");
    }

    private String statusFor(String state) {
        EspnCompetitionStatus status = state == null ? null : new EspnCompetitionStatus(new EspnStatusType(state));
        EspnEvent event = new EspnEvent(
                "1", "2026-09-07T17:00Z", new EspnEventSeason(2026), new EspnSeasonType(2, "Regular Season"),
                new EspnWeek(1),
                List.of(new EspnCompetition(
                        new EspnVenue("Venue"),
                        List.of(
                                new EspnCompetitor("home", new EspnTeam("1", "AAA", "Team A")),
                                new EspnCompetitor("away", new EspnTeam("2", "BBB", "Team B"))
                        ),
                        status)));
        return EspnResponseMapper.toRawGames(new EspnScheduleResponse(List.of(event))).get(0).status();
    }

    @Test
    void mapsQbGameLogUsingNameIndexLookup() {
        // Real 2025 data: J.J. McCarthy vs CHI, week 1 (athlete 4433970, event 401772968)
        List<String> qbNames = List.of(
                "completions", "passingAttempts", "passingYards", "completionPct", "yardsPerPassAttempt",
                "passingTouchdowns", "interceptions", "longPassing", "sacks", "QBRating", "adjQBR",
                "rushingAttempts", "rushingYards", "yardsPerRushAttempt", "rushingTouchdowns");
        List<String> qbStats = List.of(
                "14", "23", "182", "60.9", "7.9", "0", "0", "26", "0", "85.8", "68.5", "2", "7", "3.5", "0");
        EspnGameLogResponse response = new EspnGameLogResponse(qbNames, List.of(
                new EspnGameLogSeasonType(List.of(
                        new EspnGameLogCategory(List.of(
                                new EspnGameLogEvent("401772968", qbStats)))))));

        List<RawGameStats> results = EspnResponseMapper.toRawGameStats(response, "4433970");

        assertThat(results).containsExactly(new RawGameStats(
                "401772968", "4433970",
                null, null, null,             // no receiving stats in a QB's names -- correctly absent
                2, 7,                          // rushAttempts, rushYards
                23, 14, 182, 0, 0,             // passingAttempts, completions, yards, TDs, INTs
                0,                             // total touchdowns (0 passing + 0 rushing + 0 receiving)
                new BigDecimal("7.98"), new BigDecimal("7.98")));
    }

    @Test
    void mapsSkillPositionGameLogUsingNameIndexLookup() {
        // Real names order (Jordan Addison, WR); stats values are a representative
        // synthetic line since only the column names were captured live for this athlete.
        List<String> wrNames = List.of(
                "receptions", "receivingTargets", "receivingYards", "yardsPerReception", "receivingTouchdowns",
                "longReception", "rushingAttempts", "rushingYards", "yardsPerRushAttempt", "longRushing",
                "rushingTouchdowns", "fumbles", "fumblesLost", "fumblesForced", "kicksBlocked");
        List<String> wrStats = List.of(
                "6", "9", "85", "14.2", "1", "32", "1", "5", "5.0", "5", "0", "0", "0", "0", "0");
        EspnGameLogResponse response = new EspnGameLogResponse(wrNames, List.of(
                new EspnGameLogSeasonType(List.of(
                        new EspnGameLogCategory(List.of(
                                new EspnGameLogEvent("401700001", wrStats)))))));

        List<RawGameStats> results = EspnResponseMapper.toRawGameStats(response, "4429205");

        assertThat(results).containsExactly(new RawGameStats(
                "401700001", "4429205",
                9, 6, 85,                      // targets, receptions, recYards
                1, 5,                          // rushAttempts, rushYards
                null, null, null, null, null,  // no passing stats in a WR's names -- correctly absent
                1,                              // total touchdowns (1 receiving)
                new BigDecimal("21.00"), new BigDecimal("15.00")));
    }

    @Test
    void toRawGameStatsToleratesMissingNesting() {
        assertThat(EspnResponseMapper.toRawGameStats(null, "1")).isEmpty();
        assertThat(EspnResponseMapper.toRawGameStats(new EspnGameLogResponse(List.of(), null), "1")).isEmpty();
        assertThat(EspnResponseMapper.toRawGameStats(new EspnGameLogResponse(List.of(), List.of()), "1")).isEmpty();
    }
}
