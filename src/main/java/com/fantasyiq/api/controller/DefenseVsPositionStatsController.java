package com.fantasyiq.api.controller;

import com.fantasyiq.api.dto.DefenseVsPositionComputeResponse;
import com.fantasyiq.ingestion.scheduler.DefenseVsPositionStatsComputationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/defense-vs-position")
public class DefenseVsPositionStatsController {

    private final DefenseVsPositionStatsComputationService defenseVsPositionStatsComputationService;

    public DefenseVsPositionStatsController(
            DefenseVsPositionStatsComputationService defenseVsPositionStatsComputationService) {
        this.defenseVsPositionStatsComputationService = defenseVsPositionStatsComputationService;
    }

    @PostMapping("/compute")
    public ResponseEntity<DefenseVsPositionComputeResponse> compute(
            @RequestParam int season, @RequestParam int week) {
        int rowsWritten = defenseVsPositionStatsComputationService.computeForWeek(season, week);
        return ResponseEntity.ok(new DefenseVsPositionComputeResponse(rowsWritten));
    }
}
