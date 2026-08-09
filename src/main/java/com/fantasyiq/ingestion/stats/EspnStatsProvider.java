package com.fantasyiq.ingestion.stats;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class EspnStatsProvider implements StatsProvider {

    private static final String RESILIENCE_INSTANCE = "espnApi";

    private final RestClient restClient;
    private final String baseUrl;
    private final String webBaseUrl;

    public EspnStatsProvider(RestClient.Builder restClientBuilder, EspnProperties espnProperties) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = espnProperties.baseUrl();
        this.webBaseUrl = espnProperties.webBaseUrl();
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fetchTeamsFallback")
    public List<RawTeam> fetchTeams() {
        EspnTeamsResponse response = restClient.get()
                .uri(baseUrl + "/teams")
                .retrieve()
                .body(EspnTeamsResponse.class);
        return EspnResponseMapper.toRawTeams(response);
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fetchRosterFallback")
    public List<RawAthlete> fetchRoster(String teamExternalId) {
        EspnRosterResponse response = restClient.get()
                .uri(baseUrl + "/teams/{id}/roster", teamExternalId)
                .retrieve()
                .body(EspnRosterResponse.class);
        return EspnResponseMapper.toRawAthletes(response);
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fetchScheduleFallback")
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
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fetchGameStatsFallback")
    public List<RawGameStats> fetchGameStats(String athleteExternalId, int season) {
        EspnGameLogResponse response = restClient.get()
                .uri(webBaseUrl + "/athletes/{id}/gamelog?season={season}", athleteExternalId, season)
                .retrieve()
                .body(EspnGameLogResponse.class);
        return EspnResponseMapper.toRawGameStats(response, athleteExternalId);
    }

    // Fallback methods must mirror the guarded method's signature plus a
    // trailing Throwable, and rethrow rather than degrade silently -- see
    // EspnUnavailableException.

    private List<RawTeam> fetchTeamsFallback(Throwable t) {
        throw new EspnUnavailableException("ESPN teams endpoint unavailable", t);
    }

    private List<RawAthlete> fetchRosterFallback(String teamExternalId, Throwable t) {
        throw new EspnUnavailableException("ESPN roster endpoint unavailable for team " + teamExternalId, t);
    }

    private List<RawGame> fetchScheduleFallback(String teamExternalId, int season, Throwable t) {
        throw new EspnUnavailableException("ESPN schedule endpoint unavailable for team " + teamExternalId, t);
    }

    private List<RawGameStats> fetchGameStatsFallback(String athleteExternalId, int season, Throwable t) {
        throw new EspnUnavailableException("ESPN gamelog endpoint unavailable for athlete " + athleteExternalId, t);
    }
}
