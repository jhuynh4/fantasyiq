package com.fantasyiq.api.controller;

import com.fantasyiq.analytics.trade.TradeAnalysisService;
import com.fantasyiq.api.dto.TradeAnalysisRequest;
import com.fantasyiq.api.dto.TradeAnalysisResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeAnalysisService tradeAnalysisService;

    public TradeController(TradeAnalysisService tradeAnalysisService) {
        this.tradeAnalysisService = tradeAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<TradeAnalysisResponse> analyze(@Valid @RequestBody TradeAnalysisRequest request) {
        var result = tradeAnalysisService.analyze(request.sideAPlayerIds(), request.sideBPlayerIds());
        return ResponseEntity.ok(TradeAnalysisResponse.from(result));
    }
}
