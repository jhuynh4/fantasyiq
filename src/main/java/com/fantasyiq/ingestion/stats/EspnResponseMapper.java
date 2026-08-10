package com.fantasyiq.ingestion.stats;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * ESPN's gamelog is column-oriented: "names" says what each index in an
     * event's "stats" array means, and the set of names varies by position
     * (a QB has no "receptions", a WR has no "passingYards"). Looking values
     * up by name via indexOf handles that generically -- a stat simply not
     * being in "names" for this position naturally yields null, with no
     * position-specific branching needed.
     */
    static List<RawGameStats> toRawGameStats(EspnGameLogResponse response, String espnAthleteId) {
        if (response == null || response.names() == null || response.seasonTypes() == null) {
            return List.of();
        }
        List<String> names = response.names();
        Map<String, EspnGameLogEventMeta> eventMeta = response.events() != null ? response.events() : Map.of();
        return response.seasonTypes().stream()
                .filter(Objects::nonNull)
                .flatMap(seasonType -> seasonType.categories() == null
                        ? Stream.<EspnGameLogCategory>empty() : seasonType.categories().stream())
                .filter(Objects::nonNull)
                .flatMap(category -> category.events() == null
                        ? Stream.<EspnGameLogEvent>empty() : category.events().stream())
                .filter(Objects::nonNull)
                .filter(event -> event.stats() != null)
                .map(event -> toRawGameStats(names, event, espnAthleteId, eventMeta))
                .toList();
    }

    private static RawGameStats toRawGameStats(List<String> names, EspnGameLogEvent event, String espnAthleteId,
                                                Map<String, EspnGameLogEventMeta> eventMeta) {
        List<String> stats = event.stats();
        EspnGameLogEventMeta meta = eventMeta.get(event.eventId());
        String espnTeamId = meta != null && meta.team() != null ? meta.team().id() : null;

        Integer passingAttempts = statValue(names, stats, "passingAttempts");
        Integer passingCompletions = statValue(names, stats, "completions");
        Integer passingYards = statValue(names, stats, "passingYards");
        Integer passingTouchdowns = statValue(names, stats, "passingTouchdowns");
        Integer interceptions = statValue(names, stats, "interceptions");
        Integer rushAttempts = statValue(names, stats, "rushingAttempts");
        Integer rushYards = statValue(names, stats, "rushingYards");
        Integer rushingTouchdowns = statValue(names, stats, "rushingTouchdowns");
        Integer targets = statValue(names, stats, "receivingTargets");
        Integer receptions = statValue(names, stats, "receptions");
        Integer recYards = statValue(names, stats, "receivingYards");
        Integer receivingTouchdowns = statValue(names, stats, "receivingTouchdowns");
        Integer fumblesLost = statValue(names, stats, "fumblesLost");

        int totalTouchdowns = nvl(passingTouchdowns) + nvl(rushingTouchdowns) + nvl(receivingTouchdowns);

        BigDecimal pprPoints = FantasyPointsCalculator.calculate(true, passingYards, passingTouchdowns,
                interceptions, rushYards, rushingTouchdowns, receptions, recYards, receivingTouchdowns, fumblesLost);
        BigDecimal standardPoints = FantasyPointsCalculator.calculate(false, passingYards, passingTouchdowns,
                interceptions, rushYards, rushingTouchdowns, receptions, recYards, receivingTouchdowns, fumblesLost);

        return new RawGameStats(event.eventId(), espnAthleteId, espnTeamId, targets, receptions, recYards,
                rushAttempts, rushYards, passingAttempts, passingCompletions, passingYards, passingTouchdowns,
                interceptions, totalTouchdowns, pprPoints, standardPoints);
    }

    private static Integer statValue(List<String> names, List<String> stats, String statName) {
        int index = names.indexOf(statName);
        if (index < 0 || index >= stats.size()) {
            return null;
        }
        try {
            return Integer.parseInt(stats.get(index).replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int nvl(Integer value) {
        return value != null ? value : 0;
    }
}
