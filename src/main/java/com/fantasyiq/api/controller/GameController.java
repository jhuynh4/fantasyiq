package com.fantasyiq.api.controller;

import com.fantasyiq.api.dto.GameIngestResponse;
import com.fantasyiq.ingestion.scheduler.GameIngestionService;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@Validated
public class GameController {

    private final GameIngestionService gameIngestionService;

    public GameController(GameIngestionService gameIngestionService) {
        this.gameIngestionService = gameIngestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<GameIngestResponse> ingest(
            @RequestParam(required = false) @Min(1) Integer season) {
        int gamesIngested = season != null
                ? gameIngestionService.ingestSchedules(season)
                : gameIngestionService.ingestSchedules();
        return ResponseEntity.ok(new GameIngestResponse(gamesIngested));
    }
}
