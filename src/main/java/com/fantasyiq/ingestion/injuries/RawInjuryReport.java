package com.fantasyiq.ingestion.injuries;

import java.time.LocalDate;

public record RawInjuryReport(String espnAthleteId, String status, LocalDate reportDate) {
}
