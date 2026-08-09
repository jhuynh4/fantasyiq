package com.fantasyiq.ingestion.scheduler;

import java.time.LocalDate;

/**
 * NFL seasons are named for the year they start in (the "2026 season" runs
 * Sept 2026 - Feb 2027). Jan/Feb still belong to the prior season
 * (playoffs/aftermath), so only roll over to the new year from March on.
 */
final class NflSeason {

    private NflSeason() {
    }

    static int current() {
        LocalDate today = LocalDate.now();
        return today.getMonthValue() <= 2 ? today.getYear() - 1 : today.getYear();
    }
}
