package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BettingLineRepository extends JpaRepository<BettingLine, Long> {

    Optional<BettingLine> findByGameAndTeam(Game game, Team team);
}
