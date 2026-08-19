package com.fantasyiq.api.dto;

import com.fantasyiq.analytics.trade.TradeSideValue;

import java.math.BigDecimal;
import java.util.List;

public record TradeSideResponse(List<TradePlayerValueResponse> players, BigDecimal totalValue) {

    public static TradeSideResponse from(TradeSideValue side) {
        List<TradePlayerValueResponse> players = side.players().stream().map(TradePlayerValueResponse::from).toList();
        return new TradeSideResponse(players, side.totalValue());
    }
}
