package com.fantasyiq.domain.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    // ILIKE against the raw column (not wrapped in another function) so Postgres
    // can use the pg_trgm GIN index (idx_players_name_trgm) defined in V1.
    @Query(value = "SELECT * FROM players WHERE full_name ILIKE CONCAT('%', :query, '%') "
            + "ORDER BY full_name LIMIT 25", nativeQuery = true)
    List<Player> search(@Param("query") String query);
}
