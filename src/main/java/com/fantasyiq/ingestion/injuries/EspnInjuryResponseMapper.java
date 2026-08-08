package com.fantasyiq.ingestion.injuries;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

final class EspnInjuryResponseMapper {

    private EspnInjuryResponseMapper() {
    }

    static List<RawInjuryReport> toRawInjuryReports(EspnInjuryRosterResponse response) {
        if (response == null || response.athletes() == null) {
            return List.of();
        }
        return response.athletes().stream()
                .filter(Objects::nonNull)
                .flatMap(group -> group.items() == null
                        ? Stream.<EspnInjuryAthlete>empty() : group.items().stream())
                .filter(Objects::nonNull)
                .filter(athlete -> athlete.injuries() != null && !athlete.injuries().isEmpty())
                .flatMap(athlete -> athlete.injuries().stream()
                        .filter(Objects::nonNull)
                        .map(injury -> toRawInjuryReport(athlete.id(), injury)))
                .filter(Objects::nonNull)
                .toList();
    }

    private static RawInjuryReport toRawInjuryReport(String athleteId, EspnInjuryEntry injury) {
        LocalDate reportDate = parseReportDate(injury.date());
        if (reportDate == null || injury.status() == null) {
            return null;
        }
        return new RawInjuryReport(athleteId, injury.status().toUpperCase(Locale.ROOT), reportDate);
    }

    static LocalDate parseReportDate(String date) {
        if (date == null || date.length() < 10) {
            return null;
        }
        return LocalDate.parse(date.substring(0, 10));
    }
}
