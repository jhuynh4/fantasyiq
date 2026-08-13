package com.fantasyiq.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlayerTrendingResponse(UUID playerId, String playerName, String position,
                                      BigDecimal trendContribution, String narrative) {
}
