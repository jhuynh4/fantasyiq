package com.fantasyiq.ingestion.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    /**
     * Regular season only (seasonType.type == 2) -- preseason/postseason
     * games aren't relevant to weekly fantasy scoring, so they're filtered
     * out here rather than carried into the games table.
     */
    static List<RawGame> toRawGames(EspnScheduleResponse response) {
        if (response == null || response.events() == null) {
            return List.of();
        }
        return response.events().stream()
                .filter(Objects::nonNull)
                .filter(EspnResponseMapper::isRegularSeason)
                .map(EspnResponseMapper::toRawGame)
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean isRegularSeason(EspnEvent event) {
        return event.seasonType() != null && Integer.valueOf(2).equals(event.seasonType().type());
    }

    private static RawGame toRawGame(EspnEvent event) {
        if (event.competitions() == null || event.competitions().isEmpty()) {
            return null;
        }
        EspnCompetition competition = event.competitions().get(0);
        if (competition.competitors() == null) {
            return null;
        }

        String homeTeamId = findCompetitorTeamId(competition, "home");
        String awayTeamId = findCompetitorTeamId(competition, "away");
        if (homeTeamId == null || awayTeamId == null) {
            return null;
        }

        Integer season = event.season() != null ? event.season().year() : null;
        Integer week = event.week() != null ? event.week().number() : null;
        String venue = competition.venue() != null ? competition.venue().fullName() : null;

        return new RawGame(event.id(), season, week, homeTeamId, awayTeamId,
                parseKickoff(event.date()), venue, mapStatus(competition.status()));
    }

    private static String findCompetitorTeamId(EspnCompetition competition, String homeAway) {
        return competition.competitors().stream()
                .filter(Objects::nonNull)
                .filter(competitor -> homeAway.equals(competitor.homeAway()))
                .map(EspnCompetitor::team)
                .filter(Objects::nonNull)
                .map(EspnTeam::id)
                .findFirst()
                .orElse(null);
    }

    private static String mapStatus(EspnCompetitionStatus status) {
        String state = status != null && status.type() != null ? status.type().state() : null;
        if (state == null) {
            return "SCHEDULED";
        }
        return switch (state) {
            case "in" -> "IN_PROGRESS";
            case "post" -> "FINAL";
            default -> "SCHEDULED";
        };
    }

    private static Instant parseKickoff(String date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.parse(date).toInstant();
    }
}
