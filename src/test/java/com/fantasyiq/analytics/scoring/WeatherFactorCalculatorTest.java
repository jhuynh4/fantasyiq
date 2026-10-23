package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.WeatherForecast;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherFactorCalculatorTest {

    private static final BigDecimal BASELINE = new BigDecimal("20.00");

    @Test
    void highWindPenalizesWindSensitivePositionsByAShareOfTheBaseline() {
        WeatherForecast forecast = forecast(72, 25, 10);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "QB", BASELINE);

        assertThat(result).isPresent();
        // (25 - 15) mph * 1% = 10% of 20.00
        assertThat(result.get().contribution()).isEqualByComparingTo("-2.00");
    }

    @Test
    void highWindDoesNotPenalizeRunningBacks() {
        WeatherForecast forecast = forecast(72, 25, 10);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "RB", BASELINE);

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void highPrecipitationPenalizesAllPositions() {
        WeatherForecast forecast = forecast(50, 5, 70);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "RB", BASELINE);

        assertThat(result).isPresent();
        // 3% of 20.00
        assertThat(result.get().contribution()).isEqualByComparingTo("-0.60");
    }

    @Test
    void windAndPrecipitationPenaltiesStack() {
        WeatherForecast forecast = forecast(40, 30, 80);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "WR", BASELINE);

        assertThat(result).isPresent();
        // wind (30-15) * 1% = 15%, precipitation 3% -> 18% of 20.00
        assertThat(result.get().contribution()).isEqualByComparingTo("-3.60");
    }

    @Test
    void totalPenaltyIsCappedAtAFifthOfTheBaseline() {
        WeatherForecast forecast = forecast(20, 60, 90);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "QB", BASELINE);

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("-4.00");
    }

    @Test
    void thePenaltyScalesWithThePlayersBaseline() {
        WeatherForecast forecast = forecast(72, 25, 10);

        BigDecimal star = WeatherFactorCalculator.calculate(forecast, "WR", new BigDecimal("20.00")).orElseThrow().contribution();
        BigDecimal depth = WeatherFactorCalculator.calculate(forecast, "WR", new BigDecimal("5.00")).orElseThrow().contribution();

        assertThat(star).isEqualByComparingTo(depth.multiply(BigDecimal.valueOf(4)));
    }

    @Test
    void aPlayerWithNoPositiveBaselineHasNothingToLose() {
        WeatherForecast forecast = forecast(20, 60, 90);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "QB", new BigDecimal("-1.00"));

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void mildWeatherProducesNoPenalty() {
        WeatherForecast forecast = forecast(70, 5, 10);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "QB", BASELINE);

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("0.00");
        assertThat(result.get().narrative()).isEqualTo("5 mph wind, 10% chance of precipitation -- no penalty");
    }

    @Test
    void aPenaltyNarrativeStatesTheForecastAndWhatItCosts() {
        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast(72, 25, 10), "QB", BASELINE);

        assertThat(result.orElseThrow().narrative()).isEqualTo("25 mph wind, 10% chance of precipitation -- costs 10% of the baseline");
    }

    @Test
    void aRunningBackInHighWindIsToldWhyThereIsNoPenalty() {
        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast(72, 25, 10), "RB", BASELINE);

        assertThat(result.orElseThrow().narrative())
                .isEqualTo("25 mph wind, 10% chance of precipitation -- no penalty (wind mainly affects the passing game)");
    }

    @Test
    void aForecastWithOnlyOneMeasurementStatesOnlyThat() {
        WeatherForecast windOnly = new WeatherForecast(null, 70, 8, null, null);

        assertThat(WeatherFactorCalculator.calculate(windOnly, "QB", BASELINE).orElseThrow().narrative())
                .isEqualTo("8 mph wind -- no penalty");
    }

    @Test
    void nullForecastYieldsEmptyOptional() {
        assertThat(WeatherFactorCalculator.calculate(null, "QB", BASELINE)).isEmpty();
    }

    @Test
    void forecastWithNoWindOrPrecipitationDataYieldsEmptyOptional() {
        WeatherForecast forecast = new WeatherForecast(null, 70, null, null, null);

        assertThat(WeatherFactorCalculator.calculate(forecast, "QB", BASELINE)).isEmpty();
    }

    private static WeatherForecast forecast(int temperatureF, int windMph, int precipitationPct) {
        return new WeatherForecast(null, temperatureF, windMph, precipitationPct, "test conditions");
    }
}
