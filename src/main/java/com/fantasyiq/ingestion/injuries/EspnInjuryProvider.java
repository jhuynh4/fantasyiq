package com.fantasyiq.ingestion.injuries;

import com.fantasyiq.ingestion.stats.EspnProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class EspnInjuryProvider implements InjuryProvider {

    private final RestClient restClient;
    private final String baseUrl;

    public EspnInjuryProvider(RestClient.Builder restClientBuilder, EspnProperties espnProperties) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = espnProperties.baseUrl();
    }

    @Override
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
}
