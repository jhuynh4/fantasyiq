package com.fantasyiq.ingestion.stats;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class EspnStatsProvider implements StatsProvider {

    private final RestClient restClient;
    private final String baseUrl;
    private final String webBaseUrl;

    public EspnStatsProvider(RestClient.Builder restClientBuilder, EspnProperties espnProperties) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = espnProperties.baseUrl();
        this.webBaseUrl = espnProperties.webBaseUrl();
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

    @Override
    public List<RawGame> fetchSchedule(String teamExternalId, int season) {
        // ESPN's schedule endpoint defaults to whatever season/type is
        // currently happening on the calendar (e.g. preseason in August) --
        // season + seasontype=2 must be explicit or regular-season games
        // silently don't come back.
        EspnScheduleResponse response = restClient.get()
                .uri(baseUrl + "/teams/{id}/schedule?season={season}&seasontype=2", teamExternalId, season)
                .retrieve()
                .body(EspnScheduleResponse.class);
        return EspnResponseMapper.toRawGames(response);
    }

    @Override
    public List<RawGameStats> fetchGameStats(String athleteExternalId, int season) {
        EspnGameLogResponse response = restClient.get()
                .uri(webBaseUrl + "/athletes/{id}/gamelog?season={season}", athleteExternalId, season)
                .retrieve()
                .body(EspnGameLogResponse.class);
        return EspnResponseMapper.toRawGameStats(response, athleteExternalId);
    }
}
