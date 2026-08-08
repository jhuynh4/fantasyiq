package com.fantasyiq.domain.game;

import com.fantasyiq.domain.team.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Upserts by ESPN's event id (external_ref). Important here specifically:
 * the same game shows up twice from ESPN -- once in the home team's
 * schedule fetch, once in the away team's -- so this must be idempotent
 * within a single ingestion run, not just across runs.
 */
@Service
public class GameReconciliationService {

    private final GameRepository gameRepository;

    public GameReconciliationService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    @Transactional
    public Game resolveOrCreateFromEspn(String espnEventId, Integer season, Integer week,
                                         Team homeTeam, Team awayTeam, Instant kickoff,
                                         String venue, String status) {
        Optional<Game> existing = gameRepository.findByExternalRef(espnEventId);

        if (existing.isPresent()) {
            Game game = existing.get();
            game.updateFrom(season, week, homeTeam, awayTeam, kickoff, venue, status);
            return game;
        }

        Game game = new Game(espnEventId, season, week, homeTeam, awayTeam, kickoff, venue, status);
        return gameRepository.save(game);
    }
}
