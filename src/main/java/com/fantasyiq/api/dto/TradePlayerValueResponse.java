package com.fantasyiq.api.dto;

import com.fantasyiq.analytics.trade.PlayerTradeValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TradePlayerValueResponse(UUID playerId, String playerName, String position,
                                        BigDecimal score, BigDecimal replacementLevel, BigDecimal valueAboveReplacement,
                                        List<FactorResponse> factors) {

    public static TradePlayerValueResponse from(PlayerTradeValue value) {
        List<FactorResponse> factors = value.factors().stream().map(FactorResponse::from).toList();
        return new TradePlayerValueResponse(value.playerId(), value.playerName(), value.position(),
                value.score(), value.replacementLevel(), value.valueAboveReplacement(), factors);
    }
}
