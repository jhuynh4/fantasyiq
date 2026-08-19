package com.fantasyiq.analytics.trade;

import java.math.BigDecimal;
import java.util.List;

/**
 * totalValue sums each player's valueAboveReplacement, treating a player
 * with insufficient data (null valueAboveReplacement) as contributing zero
 * to the total -- they're still listed individually with a null value so
 * the gap is visible, not silently dropped from the trade.
 */
public record TradeSideValue(List<PlayerTradeValue> players, BigDecimal totalValue) {
}
