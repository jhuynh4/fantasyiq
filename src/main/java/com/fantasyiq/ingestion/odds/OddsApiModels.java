package com.fantasyiq.ingestion.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Minimal mirrors of The Odds API's actual JSON shape, covering only the
 * fields we consume. The endpoint's response root is a JSON array (no
 * wrapper object), unlike ESPN's payloads -- OddsApiProvider deserializes
 * straight into OddsGame[]. Package-private: wire-format details owned by
 * TheOddsApiProvider/OddsResponseMapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OddsGame(String id, @JsonProperty("commence_time") String commenceTime,
                @JsonProperty("home_team") String homeTeam, @JsonProperty("away_team") String awayTeam,
                List<OddsBookmaker> bookmakers) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record OddsBookmaker(String key, List<OddsMarket> markets) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record OddsMarket(String key, List<OddsOutcome> outcomes) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record OddsOutcome(String name, Double point) {
}
