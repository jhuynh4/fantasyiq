package com.fantasyiq.ingestion.odds;

/**
 * Thrown by the circuit-breaker fallback once The Odds API calls are
 * failing consistently enough to trip the breaker. Same rationale as
 * EspnUnavailableException/WeatherUnavailableException: rethrow rather
 * than degrade to an empty result, so IngestionRunService.track() can
 * record the run as FAILED instead of a misleadingly-successful "0 lines
 * ingested".
 */
public class OddsUnavailableException extends RuntimeException {

    public OddsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
