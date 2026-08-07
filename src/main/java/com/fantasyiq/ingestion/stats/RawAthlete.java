package com.fantasyiq.ingestion.stats;

import java.time.LocalDate;

public record RawAthlete(String externalId, String fullName, String position, Integer jerseyNumber,
                          String status, LocalDate birthDate) {
}
