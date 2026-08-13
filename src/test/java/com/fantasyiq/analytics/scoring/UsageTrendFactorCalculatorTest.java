package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.PlayerGameStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UsageTrendFactorCalculatorTest {

    @Test
    void risingTargetShareProducesPositiveContributionForWr() {
        // most-recent-first: 12, 10 (recent window) then 6, 5 (prior window)
        List<PlayerGameStats> games = List.of(
                gameStats(12, null, null), gameStats(10, null, null),
                gameStats(6, null, null), gameStats(5, null, null));

        Optional<FactorResult> result = UsageTrendFactorCalculator.calculate(games, "WR");

        assertThat(result).isPresent();
        assertThat(result.get().factorType()).isEqualTo("USAGE");
        // recentAvg=11, priorAvg=5.5, delta=5.5 -> 5.5 * 1.5 = 8.25
        assertThat(result.get().contribution()).isEqualByComparingTo("8.25");
        assertThat(result.get().narrative()).contains("targets");
    }

    @Test
    void fallingUsageProducesNegativeContributionForRb() {
        // RB volume = rushAttempts + targets
        List<PlayerGameStats> games = List.of(
                gameStats(2, 8, null), gameStats(2, 8, null),
                gameStats(3, 18, null), gameStats(3, 18, null));

        Optional<FactorResult> result = UsageTrendFactorCalculator.calculate(games, "RB");

        assertThat(result).isPresent();
        // recentAvg=10, priorAvg=21, delta=-11 -> -11 * 1.5 = -16.5
        assertThat(result.get().contribution()).isEqualByComparingTo("-16.50");
        assertThat(result.get().narrative()).contains("touches");
    }

    @Test
    void usesPassingAttemptsForQb() {
        List<PlayerGameStats> games = List.of(
                gameStats(null, null, 38), gameStats(null, null, 36),
                gameStats(null, null, 30), gameStats(null, null, 28));

        Optional<FactorResult> result = UsageTrendFactorCalculator.calculate(games, "QB");

        assertThat(result).isPresent();
        assertThat(result.get().narrative()).contains("pass attempts");
    }

    @Test
    void fewerThanFourPriorGamesYieldsEmptyOptional() {
        List<PlayerGameStats> games = List.of(gameStats(10, null, null), gameStats(8, null, null));

        assertThat(UsageTrendFactorCalculator.calculate(games, "WR")).isEmpty();
        assertThat(UsageTrendFactorCalculator.calculate(null, "WR")).isEmpty();
    }

    @Test
    void extremeDeltaIsCappedAtMaxMagnitude() {
        List<PlayerGameStats> games = List.of(
                gameStats(40, null, null), gameStats(40, null, null),
                gameStats(5, null, null), gameStats(5, null, null));

        Optional<FactorResult> result = UsageTrendFactorCalculator.calculate(games, "WR");

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("20.00");
    }

    private static PlayerGameStats gameStats(Integer targets, Integer rushAttempts, Integer passingAttempts) {
        return new PlayerGameStats(null, null, null, targets, null, null, rushAttempts, null,
                passingAttempts, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
