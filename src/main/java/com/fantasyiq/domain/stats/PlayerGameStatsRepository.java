package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerGameStatsRepository extends JpaRepository<PlayerGameStats, Long> {

    Optional<PlayerGameStats> findByPlayerAndGame(Player player, Game game);
}
