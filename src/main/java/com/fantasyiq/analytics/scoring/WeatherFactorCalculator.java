package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.WeatherForecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;

/**
 * Only ever a penalty (never a bonus), expressed as a share of the player's
 * baseline. No forecast row means either a dome game or one outside
 * OpenWeatherMap's forecast window, both of which mean "no reason to
 * penalize", not "assume bad weather". Wind mainly hurts the passing game
 * (QB/WR/TE): 1% of the baseline per mph above the threshold. Precipitation
 * is a smaller, position-agnostic penalty (ball security/footing): 3%. The
 * total is capped at 20% of the baseline.
 *
 * Like VEGAS, never backtested (no historical forecasts); the percentages are
 * conservative first-principles choices, not fitted ones.
 */
public final class WeatherFactorCalculator {

    static final String FACTOR_TYPE = "WEATHER";
    static final BigDecimal MAX_PENALTY_SHARE = new BigDecimal("0.20");
    private static final int WIND_THRESHOLD_MPH = 15;
    private static final double WIND_SHARE_PER_MPH = 0.01;
    private static final int PRECIPITATION_THRESHOLD_PCT = 50;
    private static final double PRECIPITATION_SHARE = 0.03;
    private static final Set<String> WIND_SENSITIVE_POSITIONS = Set.of("QB", "WR", "TE");

    private WeatherFactorCalculator() {
    }

    public static Optional<FactorResult> calculate(WeatherForecast forecast, String position, BigDecimal baseline) {
        if (forecast == null || (forecast.getWindMph() == null && forecast.getPrecipitationPct() == null)) {
            return Optional.empty();
        }

        double windShare = 0;
        if (forecast.getWindMph() != null && WIND_SENSITIVE_POSITIONS.contains(position)
                && forecast.getWindMph() > WIND_THRESHOLD_MPH) {
            windShare = (forecast.getWindMph() - WIND_THRESHOLD_MPH) * WIND_SHARE_PER_MPH;
        }

        double precipitationShare = 0;
        if (forecast.getPrecipitationPct() != null && forecast.getPrecipitationPct() > PRECIPITATION_THRESHOLD_PCT) {
            precipitationShare = PRECIPITATION_SHARE;
        }

        double penaltyShare = Math.min(windShare + precipitationShare, MAX_PENALTY_SHARE.doubleValue());
        BigDecimal contribution = baseline.max(BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(-penaltyShare))
                .setScale(2, RoundingMode.HALF_UP);

        String narrative = penaltyShare > 0
                ? String.format("Forecast of %s mph wind and %s%% precipitation chance",
                        forecast.getWindMph(), forecast.getPrecipitationPct())
                : "Weather forecast is not expected to be a factor";

        return Optional.of(new FactorResult(FACTOR_TYPE,
                BigDecimal.valueOf(forecast.getWindMph() == null ? 0 : forecast.getWindMph()),
                MAX_PENALTY_SHARE, contribution, narrative));
    }
}
