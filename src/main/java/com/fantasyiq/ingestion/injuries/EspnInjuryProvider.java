package com.fantasyiq.ingestion.injuries;

import com.fantasyiq.ingestion.stats.EspnProperties;
import com.fantasyiq.ingestion.stats.EspnUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class EspnInjuryProvider implements InjuryProvider {

    private static final String RESILIENCE_INSTANCE = "espnApi";

    private final RestClient restClient;
    private final String baseUrl;

    public EspnInjuryProvider(RestClient.Builder restClientBuilder, EspnProperties espnProperties) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = espnProperties.baseUrl();
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fetchCurrentInjuriesFallback")
    public List<RawInjuryReport> fetchCurrentInjuries(String teamExternalId) {
        // ESPN has no dedicated injury endpoint -- status is embedded in the
        // same roster payload StatsProvider.fetchRoster also calls. See
        // docs/data-source-integration.md section 2.1.
        EspnInjuryRosterResponse response = restClient.get()
                .uri(baseUrl + "/teams/{id}/roster", teamExternalId)
                .retrieve()
                .body(EspnInjuryRosterResponse.class);
        return EspnInjuryResponseMapper.toRawInjuryReports(response);
    }

    private List<RawInjuryReport> fetchCurrentInjuriesFallback(String teamExternalId, Throwable t) {
        throw new EspnUnavailableException("ESPN roster endpoint (injuries) unavailable for team " + teamExternalId, t);
    }
}
