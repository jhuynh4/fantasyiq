package com.fantasyiq.api.dto;

import com.fantasyiq.domain.player.Player;

import java.util.UUID;

public record PlayerSummaryResponse(UUID id, String fullName, String position, String team) {

    public static PlayerSummaryResponse from(Player player) {
        String teamAbbreviation = player.getCurrentTeam() != null ? player.getCurrentTeam().getAbbreviation() : null;
        return new PlayerSummaryResponse(player.getId(), player.getFullName(), player.getPosition(), teamAbbreviation);
    }
}
