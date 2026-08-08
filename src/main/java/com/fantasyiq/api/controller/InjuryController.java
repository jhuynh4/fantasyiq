package com.fantasyiq.api.controller;

import com.fantasyiq.api.dto.InjuryIngestResponse;
import com.fantasyiq.ingestion.scheduler.InjuryIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/injuries")
public class InjuryController {

    private final InjuryIngestionService injuryIngestionService;

    public InjuryController(InjuryIngestionService injuryIngestionService) {
        this.injuryIngestionService = injuryIngestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<InjuryIngestResponse> ingest() {
        int injuryReportsIngested = injuryIngestionService.ingestInjuries();
        return ResponseEntity.ok(new InjuryIngestResponse(injuryReportsIngested));
    }
}
