package com.fantasyiq.ingestion.odds;

import java.math.BigDecimal;
import java.time.Instant;

public record RawGameOdds(String homeTeamName, String awayTeamName, Instant commenceTime, String source,
                           BigDecimal homeSpread, BigDecimal awaySpread, BigDecimal overUnder) {
}
