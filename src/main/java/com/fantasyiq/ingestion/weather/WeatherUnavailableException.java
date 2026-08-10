package com.fantasyiq.ingestion.weather;

/**
 * Thrown by the circuit-breaker fallback once OpenWeatherMap calls are
 * failing consistently enough to trip the breaker. Same rationale as
 * EspnUnavailableException: rethrow rather than degrade to an empty/absent
 * result, so IngestionRunService.track() can record the run as FAILED
 * instead of a misleadingly-successful "0 forecasts fetched".
 */
public class WeatherUnavailableException extends RuntimeException {

    public WeatherUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
