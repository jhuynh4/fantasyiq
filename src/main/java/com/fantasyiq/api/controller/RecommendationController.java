package com.fantasyiq.api.controller;

import com.fantasyiq.api.dto.BacktestResponse;
import com.fantasyiq.api.dto.RecommendationGenerateResponse;
import com.fantasyiq.api.dto.StartSitRecommendationResponse;
import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.ingestion.scheduler.BacktestComputationService;
import com.fantasyiq.ingestion.scheduler.StartSitRecommendationComputationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private static final String START_SIT_TYPE = "START_SIT";

    private final StartSitRecommendationComputationService startSitRecommendationComputationService;
    private final BacktestComputationService backtestComputationService;
    private final RecommendationRepository recommendationRepository;

    public RecommendationController(StartSitRecommendationComputationService startSitRecommendationComputationService,
                                     BacktestComputationService backtestComputationService,
                                     RecommendationRepository recommendationRepository) {
        this.startSitRecommendationComputationService = startSitRecommendationComputationService;
        this.backtestComputationService = backtestComputationService;
        this.recommendationRepository = recommendationRepository;
    }

    @PostMapping("/generate")
    public ResponseEntity<RecommendationGenerateResponse> generate(@RequestParam int season, @RequestParam int week) {
        int recommendationsGenerated = startSitRecommendationComputationService.computeForWeek(season, week);
        return ResponseEntity.ok(new RecommendationGenerateResponse(recommendationsGenerated));
    }

    @GetMapping("/start-sit")
    public ResponseEntity<List<StartSitRecommendationResponse>> startSit(
            @RequestParam int season, @RequestParam int week,
            @RequestParam(required = false) String position) {
        List<Recommendation> recommendations = position != null
                ? recommendationRepository.findBySeasonAndWeekAndTypeAndPlayer_Position(season, week, START_SIT_TYPE, position)
                : recommendationRepository.findBySeasonAndWeekAndType(season, week, START_SIT_TYPE);

        List<StartSitRecommendationResponse> response = recommendations.stream()
                .sorted(Comparator.comparing(Recommendation::getScore).reversed())
                .map(StartSitRecommendationResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/backtest")
    public ResponseEntity<BacktestResponse> backtest(@RequestParam int season) {
        return ResponseEntity.ok(BacktestResponse.from(backtestComputationService.runBacktest(season)));
    }
}
