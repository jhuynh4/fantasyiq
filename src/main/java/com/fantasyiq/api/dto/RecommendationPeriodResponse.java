package com.fantasyiq.api.dto;

import java.util.List;

/** A season and the weeks of it that have start/sit scores. */
public record RecommendationPeriodResponse(int season, List<Integer> weeks) {
}
