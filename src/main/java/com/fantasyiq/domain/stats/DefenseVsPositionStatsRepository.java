package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DefenseVsPositionStatsRepository extends JpaRepository<DefenseVsPositionStats, Long> {

    Optional<DefenseVsPositionStats> findByTeamAndSeasonAndWeekAndPosition(
            Team team, Integer season, Integer week, String position);
}
