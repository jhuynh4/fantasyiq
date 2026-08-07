package com.fantasyiq.ingestion.stats;

import org.junit.jupiter.api.Test;

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
}
