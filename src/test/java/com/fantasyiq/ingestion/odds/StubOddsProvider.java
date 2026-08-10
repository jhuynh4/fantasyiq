package com.fantasyiq.ingestion.odds;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class StubOddsProvider implements OddsProvider {

    @Override
    public List<RawGameOdds> fetchCurrentOdds() {
        return List.of(
                // Matches a game/teams IntegrationTestBase-based tests are expected to seed
                new RawGameOdds("Arizona Cardinals", "San Francisco 49ers", Instant.parse("2099-09-07T17:00:00Z"),
                        "draftkings", new BigDecimal("-3.5"), new BigDecimal("3.5"), new BigDecimal("45.5")),
                // No matching game exists -- should be skipped, not fail the batch
                new RawGameOdds("Dallas Cowboys", "New York Giants", Instant.parse("2099-09-07T17:00:00Z"),
                        "draftkings", new BigDecimal("-2.5"), new BigDecimal("2.5"), new BigDecimal("41.0")));
    }
}
