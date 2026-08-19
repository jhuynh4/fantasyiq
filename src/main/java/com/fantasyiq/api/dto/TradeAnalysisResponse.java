package com.fantasyiq.api.dto;

import com.fantasyiq.analytics.trade.TradeAnalysisResult;

import java.math.BigDecimal;

/**
 * valueDelta = sideA.totalValue() - sideB.totalValue() -- positive favors
 * side A, negative favors side B.
 */
public record TradeAnalysisResponse(TradeSideResponse sideA, TradeSideResponse sideB, BigDecimal valueDelta) {

    public static TradeAnalysisResponse from(TradeAnalysisResult result) {
        return new TradeAnalysisResponse(
                TradeSideResponse.from(result.sideA()), TradeSideResponse.from(result.sideB()), result.valueDelta());
    }
}
