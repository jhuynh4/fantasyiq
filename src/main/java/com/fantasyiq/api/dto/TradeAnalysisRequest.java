package com.fantasyiq.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record TradeAnalysisRequest(
        @NotEmpty List<UUID> sideAPlayerIds,
        @NotEmpty List<UUID> sideBPlayerIds
) {
}
