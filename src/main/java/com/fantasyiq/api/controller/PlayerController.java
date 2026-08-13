package com.fantasyiq.api.controller;

import com.fantasyiq.analytics.scoring.FactorResult;
import com.fantasyiq.analytics.scoring.UsageTrendFactorCalculator;
import com.fantasyiq.api.dto.PlayerDetailResponse;
import com.fantasyiq.api.dto.PlayerIngestResponse;
import com.fantasyiq.api.dto.PlayerSummaryResponse;
import com.fantasyiq.api.dto.PlayerTrendingResponse;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.ingestion.scheduler.PlayerIngestionService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/players")
@Validated
public class PlayerController {

    private final PlayerIngestionService playerIngestionService;
    private final PlayerRepository playerRepository;
    private final PlayerGameStatsRepository playerGameStatsRepository;

    public PlayerController(PlayerIngestionService playerIngestionService, PlayerRepository playerRepository,
                             PlayerGameStatsRepository playerGameStatsRepository) {
        this.playerIngestionService = playerIngestionService;
        this.playerRepository = playerRepository;
        this.playerGameStatsRepository = playerGameStatsRepository;
    }

    @PostMapping("/ingest")
    public ResponseEntity<PlayerIngestResponse> ingest() {
        int playersIngested = playerIngestionService.ingestRosters();
        return ResponseEntity.ok(new PlayerIngestResponse(playersIngested));
    }

    @GetMapping("/search")
    public ResponseEntity<List<PlayerSummaryResponse>> search(@RequestParam("q") @NotBlank String query) {
        List<PlayerSummaryResponse> results = playerRepository.search(query).stream()
                .map(PlayerSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlayerDetailResponse> getById(@PathVariable UUID id) {
        return playerRepository.findById(id)
                .map(PlayerDetailResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Simplest version of a trending/breakout signal, per the dev plan:
     * just the same UsageTrendFactorCalculator the scoring engine already
     * uses, run against a player's 4 most recent games with no season/week
     * boundary -- "what's this player's usage doing right now", not tied
     * to any specific week's recommendation. A player with fewer than 4
     * games on record gets a response with a null contribution/narrative
     * (not a 404 -- the player exists, there's just not enough data yet).
     */
    @GetMapping("/{id}/trending")
    public ResponseEntity<PlayerTrendingResponse> trending(@PathVariable UUID id) {
        Optional<Player> player = playerRepository.findById(id);
        if (player.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<PlayerGameStats> recentGames =
                playerGameStatsRepository.findTop4ByPlayerOrderByGame_SeasonDescGame_WeekDesc(player.get());
        Optional<FactorResult> trend = UsageTrendFactorCalculator.calculate(recentGames, player.get().getPosition());

        PlayerTrendingResponse response = new PlayerTrendingResponse(
                player.get().getId(), player.get().getFullName(), player.get().getPosition(),
                trend.map(FactorResult::contribution).orElse(null),
                trend.map(FactorResult::narrative).orElse(null));
        return ResponseEntity.ok(response);
    }
}
