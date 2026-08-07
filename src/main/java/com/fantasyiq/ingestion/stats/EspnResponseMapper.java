package com.fantasyiq.ingestion.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Pure mapping from ESPN's wire shapes to our vendor-neutral Raw* DTOs.
 * Kept separate from EspnStatsProvider (which only does the HTTP calls) so
 * this logic is testable without any network access or Spring context.
 */
final class EspnResponseMapper {

    private EspnResponseMapper() {
    }

    static List<RawTeam> toRawTeams(EspnTeamsResponse response) {
        if (response == null || response.sports() == null) {
            return List.of();
        }
        return response.sports().stream()
                .filter(Objects::nonNull)
                .flatMap(sport -> sport.leagues() == null ? Stream.<EspnLeague>empty() : sport.leagues().stream())
                .filter(Objects::nonNull)
                .flatMap(league -> league.teams() == null ? Stream.<EspnTeamWrapper>empty() : league.teams().stream())
                .filter(Objects::nonNull)
                .map(EspnTeamWrapper::team)
                .filter(Objects::nonNull)
                .map(team -> new RawTeam(team.id(), team.abbreviation(), team.displayName()))
                .toList();
    }

    static List<RawAthlete> toRawAthletes(EspnRosterResponse response) {
        if (response == null || response.athletes() == null) {
            return List.of();
        }
        return response.athletes().stream()
                .filter(Objects::nonNull)
                .flatMap(group -> group.items() == null ? Stream.<EspnAthlete>empty() : group.items().stream())
                .filter(Objects::nonNull)
                .map(EspnResponseMapper::toRawAthlete)
                .toList();
    }

    private static RawAthlete toRawAthlete(EspnAthlete athlete) {
        String position = athlete.position() != null ? athlete.position().abbreviation() : null;
        String status = athlete.status() != null && athlete.status().type() != null
                ? athlete.status().type().toUpperCase(Locale.ROOT)
                : "ACTIVE";
        return new RawAthlete(
                athlete.id(),
                athlete.fullName(),
                position,
                parseJerseyNumber(athlete.jersey()),
                status,
                parseBirthDate(athlete.dateOfBirth()));
    }

    static Integer parseJerseyNumber(String jersey) {
        if (jersey == null || jersey.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(jersey.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static LocalDate parseBirthDate(String dateOfBirth) {
        if (dateOfBirth == null || dateOfBirth.length() < 10) {
            return null;
        }
        return LocalDate.parse(dateOfBirth.substring(0, 10));
    }
}
