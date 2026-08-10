package com.fantasyiq.ingestion.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Minimal mirrors of OpenWeatherMap's 5-day/3-hour-step forecast response
 * shape, covering only the fields we consume. Package-private: wire-format
 * details owned by OpenWeatherProvider/OpenWeatherResponseMapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenWeatherForecastResponse(List<OpenWeatherEntry> list) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenWeatherEntry(long dt, OpenWeatherMain main, OpenWeatherWind wind,
                         Double pop, List<OpenWeatherCondition> weather) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenWeatherMain(Double temp) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenWeatherWind(Double speed) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenWeatherCondition(String description) {
}
