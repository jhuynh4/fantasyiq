package com.fantasyiq.analytics.trade;

import java.math.BigDecimal;

/**
 * valueDelta = sideA.totalValue() - sideB.totalValue() -- positive favors
 * side A, negative favors side B.
 */
public record TradeAnalysisResult(TradeSideValue sideA, TradeSideValue sideB, BigDecimal valueDelta) {
}
