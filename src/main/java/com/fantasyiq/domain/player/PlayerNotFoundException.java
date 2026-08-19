package com.fantasyiq.domain.player;

import java.util.UUID;

public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(UUID playerId) {
        super("No player found with id " + playerId);
    }
}
