package com.fantasyiq.domain.game;

import com.fantasyiq.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findByExternalRef(String externalRef);

    List<Game> findBySeasonAndWeek(Integer season, Integer week);

    List<Game> findByHomeTeamAndAwayTeam(Team homeTeam, Team awayTeam);
}
