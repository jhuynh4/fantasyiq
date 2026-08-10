package com.fantasyiq.api.controller;

import com.fantasyiq.api.dto.OddsIngestResponse;
import com.fantasyiq.ingestion.scheduler.OddsIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/odds")
public class OddsController {

    private final OddsIngestionService oddsIngestionService;

    public OddsController(OddsIngestionService oddsIngestionService) {
        this.oddsIngestionService = oddsIngestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<OddsIngestResponse> ingest() {
        int bettingLinesIngested = oddsIngestionService.ingestOdds();
        return ResponseEntity.ok(new OddsIngestResponse(bettingLinesIngested));
    }
}
