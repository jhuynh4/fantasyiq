package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DefenseVsPositionStatsRepository extends JpaRepository<DefenseVsPositionStats, Long> {

    Optional<DefenseVsPositionStats> findByTeamAndSeasonAndWeekAndPosition(
            Team team, Integer season, Integer week, String position);

    // Prior-weeks-only: a recommendation for week W can only use defense
    // performance data from weeks before W, since week W's own defensive
    // stats don't exist until after that week's games are played.
    List<DefenseVsPositionStats> findByTeamAndSeasonAndPositionAndWeekLessThanOrderByWeekAsc(
            Team team, Integer season, String position, Integer week);
}
