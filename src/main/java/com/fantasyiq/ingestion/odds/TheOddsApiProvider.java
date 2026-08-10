package com.fantasyiq.ingestion.odds;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class TheOddsApiProvider implements OddsProvider {

    private static final String RESILIENCE_INSTANCE = "oddsApi";

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;

    public TheOddsApiProvider(RestClient.Builder restClientBuilder, OddsApiProperties oddsApiProperties) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = oddsApiProperties.baseUrl();
        this.apiKey = oddsApiProperties.apiKey();
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fetchCurrentOddsFallback")
    public List<RawGameOdds> fetchCurrentOdds() {
        OddsGame[] response = restClient.get()
                .uri(baseUrl + "/sports/americanfootball_nfl/odds?apiKey={key}&regions=us&markets=spreads,totals&oddsFormat=american",
                        apiKey)
                .retrieve()
                .body(OddsGame[].class);
        return OddsResponseMapper.toRawGameOdds(response);
    }

    private List<RawGameOdds> fetchCurrentOddsFallback(Throwable t) {
        throw new OddsUnavailableException("The Odds API endpoint unavailable", t);
    }
}
