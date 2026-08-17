package com.fantasyiq.domain.player;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Flattened, Redis-cacheable view of a Player -- same reasoning as
 * RecommendationSnapshot: lets ingestion (which must not depend on api)
 * populate the cache with a plain value the api layer can map to its own
 * response DTO.
 */
public record PlayerSnapshot(UUID id, String fullName, String position, String team,
                              Integer jerseyNumber, String status, LocalDate birthDate) implements Serializable {

    public static PlayerSnapshot from(Player player) {
        String teamAbbreviation = player.getCurrentTeam() != null ? player.getCurrentTeam().getAbbreviation() : null;
        return new PlayerSnapshot(player.getId(), player.getFullName(), player.getPosition(), teamAbbreviation,
                player.getJerseyNumber(), player.getStatus(), player.getBirthDate());
    }
}
