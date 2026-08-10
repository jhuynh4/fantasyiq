package com.fantasyiq.ingestion.stats;

import java.math.BigDecimal;

public record RawGameStats(String espnEventId, String espnAthleteId, String espnTeamId, Integer targets,
                            Integer receptions, Integer recYards, Integer rushAttempts, Integer rushYards,
                            Integer passingAttempts, Integer passingCompletions, Integer passingYards,
                            Integer passingTouchdowns, Integer interceptions, Integer touchdowns,
                            BigDecimal fantasyPointsPpr, BigDecimal fantasyPointsStandard) {
}
