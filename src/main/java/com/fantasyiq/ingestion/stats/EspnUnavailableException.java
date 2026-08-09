package com.fantasyiq.ingestion.stats;

/**
 * Thrown by circuit-breaker fallback methods once ESPN calls are failing
 * consistently enough to trip the breaker. Deliberately rethrows rather than
 * returning an empty result -- a silently-empty ingestion run would look
 * like "0 records, all fine" instead of the real story (ESPN is down),
 * and IngestionRunService.track() needs a real exception to record the
 * run as FAILED rather than a misleadingly-successful empty one.
 */
public class EspnUnavailableException extends RuntimeException {

    public EspnUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
