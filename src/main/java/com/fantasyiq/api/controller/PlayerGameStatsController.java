package com.fantasyiq.api.controller;

import com.fantasyiq.api.dto.GameStatsIngestResponse;
import com.fantasyiq.ingestion.scheduler.GameStatsIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/player-game-stats")
public class PlayerGameStatsController {

    private final GameStatsIngestionService gameStatsIngestionService;

    public PlayerGameStatsController(GameStatsIngestionService gameStatsIngestionService) {
        this.gameStatsIngestionService = gameStatsIngestionService;
    }

    /**
     * No default season -- unlike schedule ingestion, there's no meaningful
     * "current" box-score season before games have actually been played, so
     * the caller must say which season's stats they want.
     */
    @PostMapping("/ingest")
    public ResponseEntity<GameStatsIngestResponse> ingest(@RequestParam int season) {
        GameStatsIngestionService.IngestGameStatsResult result = gameStatsIngestionService.ingestGameStats(season);
        return ResponseEntity.ok(new GameStatsIngestResponse(
                result.playersConsidered(), result.playersWithEspnId(),
                result.rawStatLinesFetched(), result.statLinesIngested()));
    }
}
