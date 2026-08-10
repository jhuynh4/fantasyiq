package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.team.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class PlayerGameStatsReconciliationService {

    private final PlayerGameStatsRepository playerGameStatsRepository;

    public PlayerGameStatsReconciliationService(PlayerGameStatsRepository playerGameStatsRepository) {
        this.playerGameStatsRepository = playerGameStatsRepository;
    }

    @Transactional
    public PlayerGameStats resolveOrCreateFromEspn(Player player, Game game, Team team, Integer targets,
                                                     Integer receptions, Integer recYards, Integer rushAttempts,
                                                     Integer rushYards, Integer passingAttempts,
                                                     Integer passingCompletions, Integer passingYards,
                                                     Integer passingTouchdowns, Integer interceptions,
                                                     Integer touchdowns, BigDecimal fantasyPointsPpr,
                                                     BigDecimal fantasyPointsStandard) {
        Optional<PlayerGameStats> existing = playerGameStatsRepository.findByPlayerAndGame(player, game);

        if (existing.isPresent()) {
            PlayerGameStats stats = existing.get();
            stats.updateFrom(team, targets, receptions, recYards, rushAttempts, rushYards, passingAttempts,
                    passingCompletions, passingYards, passingTouchdowns, interceptions, touchdowns,
                    fantasyPointsPpr, fantasyPointsStandard);
            return stats;
        }

        return playerGameStatsRepository.save(new PlayerGameStats(player, game, team, targets, receptions,
                recYards, rushAttempts, rushYards, passingAttempts, passingCompletions, passingYards,
                passingTouchdowns, interceptions, touchdowns, fantasyPointsPpr, fantasyPointsStandard));
    }
}
