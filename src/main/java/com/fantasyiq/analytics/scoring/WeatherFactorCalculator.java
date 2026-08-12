package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.WeatherForecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.Optional;

/**
 * Only ever a penalty (never a bonus) -- no forecast row means either a dome
 * game or one outside OpenWeatherMap's forecast window, both of which mean
 * "no reason to penalize", not "assume bad weather". Wind mainly hurts the
 * passing game (QB/WR/TE); precipitation is a smaller, position-agnostic
 * penalty (ball security/footing).
 */
public final class WeatherFactorCalculator {

    static final String FACTOR_TYPE = "WEATHER";
    private static final BigDecimal MAX_PENALTY = BigDecimal.valueOf(15);
    private static final int WIND_THRESHOLD_MPH = 15;
    private static final int PRECIPITATION_THRESHOLD_PCT = 50;
    private static final Set<String> WIND_SENSITIVE_POSITIONS = Set.of("QB", "WR", "TE");

    private WeatherFactorCalculator() {
    }

    public static Optional<FactorResult> calculate(WeatherForecast forecast, String position) {
        if (forecast == null || (forecast.getWindMph() == null && forecast.getPrecipitationPct() == null)) {
            return Optional.empty();
        }

        double windPenalty = 0;
        if (forecast.getWindMph() != null && WIND_SENSITIVE_POSITIONS.contains(position)
                && forecast.getWindMph() > WIND_THRESHOLD_MPH) {
            windPenalty = (forecast.getWindMph() - WIND_THRESHOLD_MPH) * 0.5;
        }

        double precipPenalty = 0;
        if (forecast.getPrecipitationPct() != null && forecast.getPrecipitationPct() > PRECIPITATION_THRESHOLD_PCT) {
            precipPenalty = 5;
        }

        double totalPenalty = Math.min(windPenalty + precipPenalty, MAX_PENALTY.doubleValue());
        BigDecimal contribution = BigDecimal.valueOf(-totalPenalty).setScale(2, RoundingMode.HALF_UP);

        String narrative = totalPenalty > 0
                ? String.format("Forecast of %s mph wind and %s%% precipitation chance",
                        forecast.getWindMph(), forecast.getPrecipitationPct())
                : "Weather forecast is not expected to be a factor";

        return Optional.of(new FactorResult(FACTOR_TYPE,
                BigDecimal.valueOf(forecast.getWindMph() == null ? 0 : forecast.getWindMph()),
                MAX_PENALTY, contribution, narrative));
    }
}
