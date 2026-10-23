package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerGameStatsRepository extends JpaRepository<PlayerGameStats, Long> {

    Optional<PlayerGameStats> findByPlayerAndGame(Player player, Game game);

    List<PlayerGameStats> findByGame(Game game);

    // Prior-weeks-only, most recent first -- same reasoning as
    // DefenseVsPositionStatsRepository's week-bound query: a week-W
    // recommendation can only use the player's own performance from
    // before week W as a trend signal.
    List<PlayerGameStats> findByPlayerAndGame_SeasonAndGame_WeekLessThanOrderByGame_WeekDesc(
            Player player, Integer season, Integer week);

    // No season boundary -- trending is "what's this player's usage doing
    // right now", not tied to a specific week's recommendation.
    List<PlayerGameStats> findTop4ByPlayerOrderByGame_SeasonDescGame_WeekDesc(Player player);

    // Exact-week lookup (not the LessThan trend query above) -- backtesting
    // needs the player's ACTUAL box score for the specific week a
    // recommendation was made for, to compare against the predicted score.
    Optional<PlayerGameStats> findByPlayerAndGame_SeasonAndGame_Week(Player player, Integer season, Integer week);

    // Trade value looks at a whole season, not at one week's prior games.
    List<PlayerGameStats> findByPlayerAndGame_Season(Player player, Integer season);

    // The most recent season with any box score at all: the season a rest-of-season value is about.
    @Query("select max(g.season) from PlayerGameStats s join s.game g")
    Optional<Integer> findLatestSeason();

    // Every player at a position in one query (with the player fetched), so valuing a whole
    // position doesn't cost a query per player.
    @Query("select s from PlayerGameStats s join fetch s.player p where s.game.season = :season and p.position = :position")
    List<PlayerGameStats> findBySeasonAndPosition(@Param("season") Integer season, @Param("position") String position);

    /** One game's PPR points for a player: just the numbers, nothing the persistence context has to track. */
    interface PlayerPoints {
        UUID getPlayerId();

        BigDecimal getPoints();
    }

    // The same, for this season's games before a week, newest first: the order the old per-player query
    // returned them in, so sums and averages come out bit-for-bit identical to it.
    @Query("select s.player.id as playerId, s.fantasyPointsPpr as points from PlayerGameStats s "
            + "where s.game.season = :season and s.game.week < :week and s.player.position = :position "
            + "and s.fantasyPointsPpr is not null order by s.game.week desc")
    List<PlayerPoints> findPointsBySeasonAndPositionAndWeekLessThan(@Param("season") Integer season,
                                                                    @Param("position") String position,
                                                                    @Param("week") Integer week);

    // For callers that only need each player's points over a whole season (last season's average).
    // Plain values rather than entities: loading thousands of tracked entities makes every later
    // query in the same transaction slower, because each one has to check them all for changes.
    @Query("select s.player.id as playerId, s.fantasyPointsPpr as points from PlayerGameStats s "
            + "where s.game.season = :season and s.player.position = :position and s.fantasyPointsPpr is not null")
    List<PlayerPoints> findPointsBySeasonAndPosition(@Param("season") Integer season, @Param("position") String position);
}
