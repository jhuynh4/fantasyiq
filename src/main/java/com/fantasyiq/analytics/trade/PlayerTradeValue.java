package com.fantasyiq.analytics.trade;

import com.fantasyiq.analytics.scoring.FactorResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * score/replacementLevel/valueAboveReplacement are null (not zero) when
 * there isn't enough data to compute them -- "we can't tell" is a
 * different answer than "worth nothing", same convention as
 * PearsonCorrelation/WeightTuningService elsewhere in analytics.
 */
public record PlayerTradeValue(UUID playerId, String playerName, String position,
                                BigDecimal score, BigDecimal replacementLevel, BigDecimal valueAboveReplacement,
                                List<FactorResult> factors) {
}
