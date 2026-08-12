package com.fantasyiq.domain.recommendation;

import com.fantasyiq.domain.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    Optional<Recommendation> findByPlayerAndSeasonAndWeekAndType(Player player, Integer season, Integer week, String type);

    List<Recommendation> findBySeasonAndWeekAndType(Integer season, Integer week, String type);

    List<Recommendation> findBySeasonAndWeekAndTypeAndPlayer_Position(
            Integer season, Integer week, String type, String position);
}
