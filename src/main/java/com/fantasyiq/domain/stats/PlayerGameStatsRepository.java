package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

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
}
