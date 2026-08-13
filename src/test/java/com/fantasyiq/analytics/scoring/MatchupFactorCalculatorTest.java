package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.DefenseVsPositionStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MatchupFactorCalculatorTest {

    @Test
    void averagesRankAcrossPriorWeeksAndFavorsEasierMatchups() {
        List<DefenseVsPositionStats> priorWeeks = List.of(
                stats(28, 1), stats(30, 2), stats(32, 3)); // easy matchups, avg rank 30

        Optional<FactorResult> result = MatchupFactorCalculator.calculate(priorWeeks, "WR");

        assertThat(result).isPresent();
        FactorResult factor = result.get();
        assertThat(factor.factorType()).isEqualTo("MATCHUP");
        assertThat(factor.factorValue()).isEqualByComparingTo("30.0");
        // favorability = (30-1)/31 = 0.9355; contribution = 0.9355 * 25 ~= 23.39
        assertThat(factor.contribution()).isEqualByComparingTo("23.39");
        assertThat(factor.narrative()).contains("WR");
    }

    @Test
    void toughMatchupsProduceLowContribution() {
        List<DefenseVsPositionStats> priorWeeks = List.of(stats(1, 1)); // toughest possible

        Optional<FactorResult> result = MatchupFactorCalculator.calculate(priorWeeks, "RB");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void emptyOrNullPriorWeeksYieldsEmptyOptional() {
        assertThat(MatchupFactorCalculator.calculate(List.of(), "QB")).isEmpty();
        assertThat(MatchupFactorCalculator.calculate(null, "QB")).isEmpty();
    }

    private static DefenseVsPositionStats stats(int rank, int week) {
        return new DefenseVsPositionStats(null, 2026, week, "WR",
                BigDecimal.valueOf(20), BigDecimal.valueOf(18), rank, rank);
    }
}
