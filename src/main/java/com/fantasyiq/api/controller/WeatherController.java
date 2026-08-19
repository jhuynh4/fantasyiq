package com.fantasyiq.api.controller;

import com.fantasyiq.api.dto.WeatherIngestResponse;
import com.fantasyiq.ingestion.scheduler.WeatherIngestionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@Validated
public class WeatherController {

    private final WeatherIngestionService weatherIngestionService;

    public WeatherController(WeatherIngestionService weatherIngestionService) {
        this.weatherIngestionService = weatherIngestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<WeatherIngestResponse> ingest(
            @RequestParam @Min(1) int season, @RequestParam @Min(1) @Max(18) int week) {
        int forecastsIngested = weatherIngestionService.ingestForecasts(season, week);
        return ResponseEntity.ok(new WeatherIngestResponse(forecastsIngested));
    }
}
