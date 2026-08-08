package com.fantasyiq.ingestion.injuries;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EspnInjuryResponseMapperTest {

    @Test
    void mapsInjuredAthleteToRawInjuryReport() {
        // Real shape captured from ESPN's roster endpoint (Gavin Bartholomew, MIN)
        EspnInjuryAthlete athlete = new EspnInjuryAthlete("1", List.of(
                new EspnInjuryEntry("Questionable", "2026-02-13T01:07Z")));
        EspnInjuryRosterResponse response = new EspnInjuryRosterResponse(
                List.of(new EspnInjuryPositionGroup(List.of(athlete))));

        List<RawInjuryReport> reports = EspnInjuryResponseMapper.toRawInjuryReports(response);

        assertThat(reports).containsExactly(
                new RawInjuryReport("1", "QUESTIONABLE", LocalDate.of(2026, 2, 13)));
    }

    @Test
    void healthyAthletesWithEmptyInjuriesArrayAreExcluded() {
        // Real shape: a healthy player's injuries array is present but empty
        EspnInjuryAthlete athlete = new EspnInjuryAthlete("2", List.of());
        EspnInjuryRosterResponse response = new EspnInjuryRosterResponse(
                List.of(new EspnInjuryPositionGroup(List.of(athlete))));

        assertThat(EspnInjuryResponseMapper.toRawInjuryReports(response)).isEmpty();
    }

    @Test
    void toleratesMissingNesting() {
        assertThat(EspnInjuryResponseMapper.toRawInjuryReports(null)).isEmpty();
        assertThat(EspnInjuryResponseMapper.toRawInjuryReports(new EspnInjuryRosterResponse(null))).isEmpty();

        EspnInjuryAthlete athlete = new EspnInjuryAthlete("1", null);
        EspnInjuryRosterResponse response = new EspnInjuryRosterResponse(
                List.of(new EspnInjuryPositionGroup(List.of(athlete))));
        assertThat(EspnInjuryResponseMapper.toRawInjuryReports(response)).isEmpty();
    }

    @Test
    void parseReportDateExtractsDatePortionOfIsoTimestamp() {
        assertThat(EspnInjuryResponseMapper.parseReportDate("2026-02-13T01:07Z")).isEqualTo(LocalDate.of(2026, 2, 13));
        assertThat(EspnInjuryResponseMapper.parseReportDate(null)).isNull();
        assertThat(EspnInjuryResponseMapper.parseReportDate("")).isNull();
    }
}
