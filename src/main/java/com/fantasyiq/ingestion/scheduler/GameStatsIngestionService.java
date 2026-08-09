package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerExternalId;
import com.fantasyiq.domain.player.PlayerExternalIdRepository;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.stats.PlayerGameStatsReconciliationService;
import com.fantasyiq.ingestion.stats.RawGameStats;
import com.fantasyiq.ingestion.stats.StatsProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class GameStatsIngestionService {

    private static final String ESPN_SOURCE = "ESPN";

    // ESPN's per-athlete gamelog only covers offensive skill positions;
    // K/DST are team-level stats from an entirely different endpoint, out
    // of scope here.
    private static final Set<String> SUPPORTED_POSITIONS = Set.of("QB", "RB", "WR", "TE");

    private final StatsProvider statsProvider;
    private final PlayerRepository playerRepository;
    private final PlayerExternalIdRepository playerExternalIdRepository;
    private final GameRepository gameRepository;
    private final PlayerGameStatsReconciliationService playerGameStatsReconciliationService;

    public GameStatsIngestionService(StatsProvider statsProvider,
                                      PlayerRepository playerRepository,
                                      PlayerExternalIdRepository playerExternalIdRepository,
                                      GameRepository gameRepository,
                                      PlayerGameStatsReconciliationService playerGameStatsReconciliationService) {
        this.statsProvider = statsProvider;
        this.playerRepository = playerRepository;
        this.playerExternalIdRepository = playerExternalIdRepository;
        this.gameRepository = gameRepository;
        this.playerGameStatsReconciliationService = playerGameStatsReconciliationService;
    }

    /**
     * Per-athlete, not per-team -- this is a much bigger job than the other
     * three (hundreds of HTTP calls, not ~32). No default season: unlike
     * schedules, there's no meaningful "current" box-score season before
     * games have actually been played, so the caller must be explicit.
     *
     * Requires the games table to already have this season's schedule
     * ingested (see GameIngestionService.ingestSchedules(season)) -- a stat
     * line for a game we don't have is skipped, not a failure, since that's
     * an ordering gap between two jobs, not corrupt data.
     */
    public IngestGameStatsResult ingestGameStats(int season) {
        int playersConsidered = 0;
        int playersWithEspnId = 0;
        int rawStatLinesFetched = 0;
        int statLinesIngested = 0;
        Set<String> sampleUnmatchedEventIds = new LinkedHashSet<>();
        Set<String> sampleStoredExternalRefs = new LinkedHashSet<>();
        gameRepository.findAll().stream().limit(5).forEach(g -> sampleStoredExternalRefs.add(g.getExternalRef()));

        for (Player player : playerRepository.findAll()) {
            if (!SUPPORTED_POSITIONS.contains(player.getPosition())) {
                continue;
            }
            playersConsidered++;

            Optional<String> espnAthleteId = playerExternalIdRepository
                    .findByPlayerAndSource(player, ESPN_SOURCE)
                    .map(PlayerExternalId::getExternalId);
            if (espnAthleteId.isEmpty()) {
                continue;
            }
            playersWithEspnId++;

            for (RawGameStats rawStats : statsProvider.fetchGameStats(espnAthleteId.get(), season)) {
                rawStatLinesFetched++;
                Optional<Game> game = gameRepository.findByExternalRef(rawStats.espnEventId());
                if (game.isEmpty()) {
                    if (sampleUnmatchedEventIds.size() < 5) {
                        sampleUnmatchedEventIds.add(rawStats.espnEventId());
                    }
                    continue;
                }

                playerGameStatsReconciliationService.resolveOrCreateFromEspn(
                        player, game.get(), rawStats.targets(), rawStats.receptions(), rawStats.recYards(),
                        rawStats.rushAttempts(), rawStats.rushYards(), rawStats.passingAttempts(),
                        rawStats.passingCompletions(), rawStats.passingYards(), rawStats.passingTouchdowns(),
                        rawStats.interceptions(), rawStats.touchdowns(), rawStats.fantasyPointsPpr(),
                        rawStats.fantasyPointsStandard());
                statLinesIngested++;
            }
        }

        return new IngestGameStatsResult(playersConsidered, playersWithEspnId, rawStatLinesFetched,
                statLinesIngested, sampleUnmatchedEventIds, sampleStoredExternalRefs);
    }

    public record IngestGameStatsResult(int playersConsidered, int playersWithEspnId, int rawStatLinesFetched,
                                         int statLinesIngested, Set<String> sampleUnmatchedEventIds,
                                         Set<String> sampleStoredExternalRefs) {
    }
}
