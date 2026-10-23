package com.fantasyiq.api.controller;

import com.fantasyiq.analytics.backtest.WeightTuningService;
import com.fantasyiq.api.dto.BacktestResponse;
import com.fantasyiq.api.dto.RecommendationGenerateResponse;
import com.fantasyiq.api.dto.RecommendationPeriodResponse;
import com.fantasyiq.api.dto.StartSitRecommendationResponse;
import com.fantasyiq.api.dto.WeightTuningResponse;
import com.fantasyiq.cache.RecommendationCacheService;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.ingestion.scheduler.BacktestComputationService;
import com.fantasyiq.ingestion.scheduler.StartSitRecommendationComputationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@Validated
public class RecommendationController {

    private final StartSitRecommendationComputationService startSitRecommendationComputationService;
    private final BacktestComputationService backtestComputationService;
    private final WeightTuningService weightTuningService;
    private final RecommendationCacheService recommendationCacheService;
    private final RecommendationRepository recommendationRepository;

    public RecommendationController(StartSitRecommendationComputationService startSitRecommendationComputationService,
                                     BacktestComputationService backtestComputationService,
                                     WeightTuningService weightTuningService,
                                     RecommendationCacheService recommendationCacheService,
                                     RecommendationRepository recommendationRepository) {
        this.startSitRecommendationComputationService = startSitRecommendationComputationService;
        this.backtestComputationService = backtestComputationService;
        this.weightTuningService = weightTuningService;
        this.recommendationCacheService = recommendationCacheService;
        this.recommendationRepository = recommendationRepository;
    }

    @PostMapping("/generate")
    public ResponseEntity<RecommendationGenerateResponse> generate(
            @RequestParam @Min(1) int season, @RequestParam @Min(1) @Max(18) int week) {
        int recommendationsGenerated = startSitRecommendationComputationService.computeForWeek(season, week);
        return ResponseEntity.ok(new RecommendationGenerateResponse(recommendationsGenerated));
    }

    @GetMapping("/start-sit")
    public ResponseEntity<List<StartSitRecommendationResponse>> startSit(
            @RequestParam @Min(1) int season, @RequestParam @Min(1) @Max(18) int week,
            @RequestParam(required = false) String position) {
        List<StartSitRecommendationResponse> response = recommendationCacheService.getStartSit(season, week, position).stream()
                .map(StartSitRecommendationResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /** The seasons and weeks that have start/sit scores, newest season first, so a client can offer only those. */
    @GetMapping("/periods")
    public ResponseEntity<List<RecommendationPeriodResponse>> periods() {
        List<RecommendationPeriodResponse> response = new ArrayList<>();
        Integer currentSeason = null;
        List<Integer> weeks = null;
        for (RecommendationRepository.Period period : recommendationRepository.findPeriodsByType("START_SIT")) {
            if (!period.getSeason().equals(currentSeason)) {
                currentSeason = period.getSeason();
                weeks = new ArrayList<>();
                response.add(new RecommendationPeriodResponse(currentSeason, weeks));
            }
            weeks.add(period.getWeek());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/backtest")
    public ResponseEntity<BacktestResponse> backtest(@RequestParam @Min(1) int season) {
        return ResponseEntity.ok(BacktestResponse.from(backtestComputationService.runBacktest(season)));
    }

    @GetMapping("/tune-weights")
    public ResponseEntity<WeightTuningResponse> tuneWeights(@RequestParam @Min(1) int season) {
        return ResponseEntity.ok(WeightTuningResponse.from(weightTuningService.analyzeWeights(season)));
    }
}
