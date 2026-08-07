package com.fantasyiq.ingestion.stats;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class EspnStatsProvider implements StatsProvider {

    private final RestClient restClient;
    private final String baseUrl;

    public EspnStatsProvider(RestClient.Builder restClientBuilder, EspnProperties espnProperties) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = espnProperties.baseUrl();
    }

    @Override
    public List<RawTeam> fetchTeams() {
        EspnTeamsResponse response = restClient.get()
                .uri(baseUrl + "/teams")
                .retrieve()
                .body(EspnTeamsResponse.class);
        return EspnResponseMapper.toRawTeams(response);
    }

    @Override
    public List<RawAthlete> fetchRoster(String teamExternalId) {
        EspnRosterResponse response = restClient.get()
                .uri(baseUrl + "/teams/{id}/roster", teamExternalId)
                .retrieve()
                .body(EspnRosterResponse.class);
        return EspnResponseMapper.toRawAthletes(response);
    }
}
