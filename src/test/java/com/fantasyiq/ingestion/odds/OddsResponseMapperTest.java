package com.fantasyiq.ingestion.odds;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OddsResponseMapperTest {

    @Test
    void mapsSpreadsAndTotalsFromTheFirstBookmaker() {
        OddsGame game = new OddsGame("evt-1", "2026-09-07T17:00:00Z", "Kansas City Chiefs", "Baltimore Ravens", List.of(
                new OddsBookmaker("draftkings", List.of(
                        new OddsMarket("spreads", List.of(
                                new OddsOutcome("Kansas City Chiefs", -3.5),
                                new OddsOutcome("Baltimore Ravens", 3.5))),
                        new OddsMarket("totals", List.of(
                                new OddsOutcome("Over", 45.5),
                                new OddsOutcome("Under", 45.5))))),
                new OddsBookmaker("fanduel", List.of())));

        List<RawGameOdds> odds = OddsResponseMapper.toRawGameOdds(new OddsGame[]{game});

        assertThat(odds).containsExactly(new RawGameOdds("Kansas City Chiefs", "Baltimore Ravens",
                Instant.parse("2026-09-07T17:00:00Z"), "draftkings",
                new BigDecimal("-3.5"), new BigDecimal("3.5"), new BigDecimal("45.5")));
    }

    @Test
    void skipsGamesMissingBookmakersOrRequiredMarkets() {
        OddsGame noBookmakers = new OddsGame("evt-2", "2026-09-07T17:00:00Z", "Dallas Cowboys", "New York Giants", List.of());
        OddsGame noSpreadsMarket = new OddsGame("evt-3", "2026-09-07T17:00:00Z", "Green Bay Packers", "Chicago Bears", List.of(
                new OddsBookmaker("draftkings", List.of(
                        new OddsMarket("totals", List.of(new OddsOutcome("Over", 44.0)))))));

        List<RawGameOdds> odds = OddsResponseMapper.toRawGameOdds(new OddsGame[]{noBookmakers, noSpreadsMarket});

        assertThat(odds).isEmpty();
    }

    @Test
    void nullGamesArrayYieldsEmptyList() {
        assertThat(OddsResponseMapper.toRawGameOdds(null)).isEmpty();
    }
}
