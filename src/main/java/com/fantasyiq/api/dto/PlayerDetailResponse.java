package com.fantasyiq.api.dto;

import com.fantasyiq.domain.player.PlayerSnapshot;

import java.time.LocalDate;
import java.util.UUID;

public record PlayerDetailResponse(UUID id, String fullName, String position, String team,
                                    Integer jerseyNumber, String status, LocalDate birthDate) {

    public static PlayerDetailResponse from(PlayerSnapshot snapshot) {
        return new PlayerDetailResponse(
                snapshot.id(), snapshot.fullName(), snapshot.position(), snapshot.team(),
                snapshot.jerseyNumber(), snapshot.status(), snapshot.birthDate());
    }
}
