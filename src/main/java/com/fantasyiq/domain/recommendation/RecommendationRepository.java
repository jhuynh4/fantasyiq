package com.fantasyiq.domain.recommendation;

import com.fantasyiq.domain.player.Player;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    Optional<Recommendation> findByPlayerAndSeasonAndWeekAndType(Player player, Integer season, Integer week, String type);

    // Recommendation.player and .factors are EAGER, but a query method doesn't honor that with a
    // join -- Hibernate loads them with one follow-up query per row (1 + 2N statements for N rows:
    // about 1,600 for a normal week). The graph pulls the player, the player's team and the factors
    // into this one query instead.
    @EntityGraph(attributePaths = {"player", "player.currentTeam", "factors"})
    List<Recommendation> findBySeasonAndWeekAndType(Integer season, Integer week, String type);

    @EntityGraph(attributePaths = {"player", "player.currentTeam", "factors"})
    List<Recommendation> findBySeasonAndType(Integer season, String type);
}
