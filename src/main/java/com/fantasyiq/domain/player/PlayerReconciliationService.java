package com.fantasyiq.domain.player;

import com.fantasyiq.domain.team.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Owns cross-source player identity matching (see docs/data-source-integration.md).
 * ESPN is the primary onboarding source: an unseen ESPN athlete id becomes a new
 * canonical Player row directly, no fuzzy matching needed. Fuzzy name-matching
 * (for sources like Sleeper that don't share ESPN's athlete id) is a later addition.
 */
@Service
public class PlayerReconciliationService {

    private static final String ESPN_SOURCE = "ESPN";

    private final PlayerRepository playerRepository;
    private final PlayerExternalIdRepository playerExternalIdRepository;

    public PlayerReconciliationService(PlayerRepository playerRepository,
                                        PlayerExternalIdRepository playerExternalIdRepository) {
        this.playerRepository = playerRepository;
        this.playerExternalIdRepository = playerExternalIdRepository;
    }

    @Transactional
    public Player resolveOrCreateFromEspn(String espnAthleteId, String fullName, String position,
                                           Team team, Integer jerseyNumber, String status, LocalDate birthDate) {
        Optional<PlayerExternalId> existingRef =
                playerExternalIdRepository.findBySourceAndExternalId(ESPN_SOURCE, espnAthleteId);

        if (existingRef.isPresent()) {
            Player player = existingRef.get().getPlayer();
            player.updateFrom(fullName, position, team, jerseyNumber, status, birthDate);
            return player;
        }

        Player player = new Player(fullName, position, team, jerseyNumber, status, birthDate);
        playerRepository.save(player);
        playerExternalIdRepository.save(new PlayerExternalId(player, ESPN_SOURCE, espnAthleteId));
        return player;
    }
}
