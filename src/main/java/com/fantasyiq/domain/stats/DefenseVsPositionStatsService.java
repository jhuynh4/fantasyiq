package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.team.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Computed, not ingested: aggregates player_game_stats + games (already in
 * the DB) rather than calling any external API. For each defending team and
 * offensive position, sums fantasy points allowed that week and ranks all
 * teams within that position (1 = fewest allowed = toughest matchup).
 *
 * Stat lines whose team wasn't resolved during ingestion (player_game_stats
 * .team_id is nullable -- ESPN occasionally omits it) are skipped: without
 * knowing the offense's team there's no way to tell which defense they
 * actually faced.
 *
 * Grouping/comparing teams by getId() (Integer) throughout rather than the
 * Team entity itself -- entities here don't override equals()/hashCode(),
 * so relying on reference equality (even though it happens to hold within
 * one Hibernate session) would be fragile.
 */
@Service
public class DefenseVsPositionStatsService {

    private static final List<String> POSITIONS = List.of("QB", "RB", "WR", "TE");

    private final GameRepository gameRepository;
    private final PlayerGameStatsRepository playerGameStatsRepository;
    private final DefenseVsPositionStatsRepository defenseVsPositionStatsRepository;

    public DefenseVsPositionStatsService(GameRepository gameRepository,
                                          PlayerGameStatsRepository playerGameStatsRepository,
                                          DefenseVsPositionStatsRepository defenseVsPositionStatsRepository) {
        this.gameRepository = gameRepository;
        this.playerGameStatsRepository = playerGameStatsRepository;
        this.defenseVsPositionStatsRepository = defenseVsPositionStatsRepository;
    }

    @Transactional
    public int computeForWeek(int season, int week) {
        Map<Integer, Team> teamsById = new LinkedHashMap<>();
        Map<Integer, Map<String, Totals>> allowedByTeamIdAndPosition = new LinkedHashMap<>();

        for (Game game : gameRepository.findBySeasonAndWeek(season, week)) {
            for (PlayerGameStats stats : playerGameStatsRepository.findByGame(game)) {
                Team offenseTeam = stats.getTeam();
                if (offenseTeam == null) {
                    continue;
                }
                Team defendingTeam = resolveOpponent(game, offenseTeam);
                if (defendingTeam == null) {
                    continue;
                }
                String position = stats.getPlayer().getPosition();
                if (!POSITIONS.contains(position)) {
                    continue;
                }

                teamsById.putIfAbsent(defendingTeam.getId(), defendingTeam);
                allowedByTeamIdAndPosition
                        .computeIfAbsent(defendingTeam.getId(), id -> new LinkedHashMap<>())
                        .merge(position, Totals.of(stats), Totals::plus);
            }
        }

        int rowsWritten = 0;
        for (String position : POSITIONS) {
            Map<Integer, Totals> totalsByTeamId = new LinkedHashMap<>();
            for (Map.Entry<Integer, Map<String, Totals>> entry : allowedByTeamIdAndPosition.entrySet()) {
                Totals totals = entry.getValue().get(position);
                if (totals != null) {
                    totalsByTeamId.put(entry.getKey(), totals);
                }
            }

            Map<Integer, Integer> pprRanks = rankBy(totalsByTeamId, Totals::ppr);
            Map<Integer, Integer> standardRanks = rankBy(totalsByTeamId, Totals::standard);

            for (Map.Entry<Integer, Totals> entry : totalsByTeamId.entrySet()) {
                Integer teamId = entry.getKey();
                Totals totals = entry.getValue();
                resolveOrCreate(teamsById.get(teamId), season, week, position,
                        totals.ppr(), totals.standard(), pprRanks.get(teamId), standardRanks.get(teamId));
                rowsWritten++;
            }
        }

        return rowsWritten;
    }

    private Team resolveOpponent(Game game, Team offenseTeam) {
        if (offenseTeam.getId().equals(game.getHomeTeam().getId())) {
            return game.getAwayTeam();
        }
        if (offenseTeam.getId().equals(game.getAwayTeam().getId())) {
            return game.getHomeTeam();
        }
        return null;
    }

    private Map<Integer, Integer> rankBy(Map<Integer, Totals> totalsByTeamId, Function<Totals, BigDecimal> value) {
        List<Map.Entry<Integer, Totals>> sorted = new ArrayList<>(totalsByTeamId.entrySet());
        sorted.sort(Comparator.comparing(entry -> value.apply(entry.getValue())));
        Map<Integer, Integer> ranks = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            ranks.put(sorted.get(i).getKey(), i + 1);
        }
        return ranks;
    }

    private void resolveOrCreate(Team team, int season, int week, String position,
                                  BigDecimal pprAllowed, BigDecimal standardAllowed,
                                  int rankPpr, int rankStandard) {
        Optional<DefenseVsPositionStats> existing = defenseVsPositionStatsRepository
                .findByTeamAndSeasonAndWeekAndPosition(team, season, week, position);

        if (existing.isPresent()) {
            existing.get().updateFrom(pprAllowed, standardAllowed, rankPpr, rankStandard);
        } else {
            defenseVsPositionStatsRepository.save(new DefenseVsPositionStats(
                    team, season, week, position, pprAllowed, standardAllowed, rankPpr, rankStandard));
        }
    }

    private record Totals(BigDecimal ppr, BigDecimal standard) {
        static Totals of(PlayerGameStats stats) {
            BigDecimal ppr = stats.getFantasyPointsPpr() != null ? stats.getFantasyPointsPpr() : BigDecimal.ZERO;
            BigDecimal standard = stats.getFantasyPointsStandard() != null
                    ? stats.getFantasyPointsStandard() : BigDecimal.ZERO;
            return new Totals(ppr, standard);
        }

        Totals plus(Totals other) {
            return new Totals(ppr.add(other.ppr), standard.add(other.standard));
        }
    }
}
