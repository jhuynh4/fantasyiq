package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.WeatherForecast;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherFactorCalculatorTest {

    @Test
    void highWindPenalizesWindSensitivePositions() {
        WeatherForecast forecast = forecast(72, 25, 10);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "QB");

        assertThat(result).isPresent();
        // (25 - 15) * 0.5 = 5.0
        assertThat(result.get().contribution()).isEqualByComparingTo("-5.00");
    }

    @Test
    void highWindDoesNotPenalizeRunningBacks() {
        WeatherForecast forecast = forecast(72, 25, 10);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "RB");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void highPrecipitationPenalizesAllPositions() {
        WeatherForecast forecast = forecast(50, 5, 70);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "RB");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("-5.00");
    }

    @Test
    void windAndPrecipitationPenaltiesStack() {
        WeatherForecast forecast = forecast(40, 30, 80);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "WR");

        assertThat(result).isPresent();
        // wind: (30-15)*0.5 = 7.5, precip: 5.0 -> 12.5
        assertThat(result.get().contribution()).isEqualByComparingTo("-12.50");
    }

    @Test
    void totalPenaltyIsCappedAtMax() {
        WeatherForecast forecast = forecast(20, 60, 90);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "QB");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("-15.00");
    }

    @Test
    void mildWeatherProducesNoPenalty() {
        WeatherForecast forecast = forecast(70, 5, 10);

        Optional<FactorResult> result = WeatherFactorCalculator.calculate(forecast, "QB");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("0.00");
        assertThat(result.get().narrative()).contains("not expected to be a factor");
    }

    @Test
    void nullForecastYieldsEmptyOptional() {
        assertThat(WeatherFactorCalculator.calculate(null, "QB")).isEmpty();
    }

    @Test
    void forecastWithNoWindOrPrecipitationDataYieldsEmptyOptional() {
        WeatherForecast forecast = new WeatherForecast(null, 70, null, null, null);

        assertThat(WeatherFactorCalculator.calculate(forecast, "QB")).isEmpty();
    }

    private static WeatherForecast forecast(int temperatureF, int windMph, int precipitationPct) {
        return new WeatherForecast(null, temperatureF, windMph, precipitationPct, "test conditions");
    }
}
