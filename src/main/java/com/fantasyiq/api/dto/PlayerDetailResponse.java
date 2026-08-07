package com.fantasyiq.api.dto;

import com.fantasyiq.domain.player.Player;

import java.time.LocalDate;
import java.util.UUID;

public record PlayerDetailResponse(UUID id, String fullName, String position, String team,
                                    Integer jerseyNumber, String status, LocalDate birthDate) {

    public static PlayerDetailResponse from(Player player) {
        String teamAbbreviation = player.getCurrentTeam() != null ? player.getCurrentTeam().getAbbreviation() : null;
        return new PlayerDetailResponse(
                player.getId(), player.getFullName(), player.getPosition(), teamAbbreviation,
                player.getJerseyNumber(), player.getStatus(), player.getBirthDate());
    }
}
