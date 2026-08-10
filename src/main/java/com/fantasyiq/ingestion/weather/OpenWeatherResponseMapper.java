package com.fantasyiq.ingestion.weather;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * OpenWeatherMap's forecast endpoint returns a fixed 3-hour-step timeline,
 * never an entry for the exact kickoff instant -- pick whichever entry's
 * timestamp is closest to kickoff rather than requiring an exact match.
 */
final class OpenWeatherResponseMapper {

    private OpenWeatherResponseMapper() {
    }

    static Optional<RawWeatherForecast> toRawWeatherForecast(OpenWeatherForecastResponse response, Instant kickoff) {
        if (response == null || response.list() == null || response.list().isEmpty()) {
            return Optional.empty();
        }

        OpenWeatherEntry closest = response.list().stream()
                .min(Comparator.comparingLong(entry -> Math.abs(entry.dt() - kickoff.getEpochSecond())))
                .orElseThrow();

        Integer temperatureF = closest.main() != null && closest.main().temp() != null
                ? (int) Math.round(closest.main().temp()) : null;
        Integer windMph = closest.wind() != null && closest.wind().speed() != null
                ? (int) Math.round(closest.wind().speed()) : null;
        Integer precipitationPct = closest.pop() != null
                ? (int) Math.round(closest.pop() * 100) : null;
        String conditions = firstConditionDescription(closest.weather());

        return Optional.of(new RawWeatherForecast(temperatureF, windMph, precipitationPct, conditions));
    }

    private static String firstConditionDescription(List<OpenWeatherCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        return conditions.get(0).description();
    }
}
