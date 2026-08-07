package com.fantasyiq.api.controller;

import com.fantasyiq.api.dto.PlayerDetailResponse;
import com.fantasyiq.api.dto.PlayerIngestResponse;
import com.fantasyiq.api.dto.PlayerSummaryResponse;
import com.fantasyiq.domain.player.PlayerRepository;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/players")
@Validated
public class PlayerController {

    private final PlayerIngestionService playerIngestionService;
    private final PlayerRepository playerRepository;

    public PlayerController(PlayerIngestionService playerIngestionService, PlayerRepository playerRepository) {
        this.playerIngestionService = playerIngestionService;
        this.playerRepository = playerRepository;
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
}
