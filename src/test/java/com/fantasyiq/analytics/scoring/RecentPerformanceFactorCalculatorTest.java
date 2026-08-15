package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.PlayerGameStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecentPerformanceFactorCalculatorTest {

    @Test
    void usesOnlyTheSingleMostRecentGamesPoints() {
        List<PlayerGameStats> priorGames = List.of(
                gameStats(new BigDecimal("18.40")), gameStats(new BigDecimal("6.00")), gameStats(new BigDecimal("30.00")));

        Optional<FactorResult> result = RecentPerformanceFactorCalculator.calculate(priorGames);

        assertThat(result).isPresent();
        assertThat(result.get().factorType()).isEqualTo("RECENT_PERFORMANCE");
        assertThat(result.get().factorValue()).isEqualByComparingTo("18.40");
        // 18.40 * 0.53 = 9.752 -> 9.75 (HALF_UP)
        assertThat(result.get().contribution()).isEqualByComparingTo("9.75");
        assertThat(result.get().narrative()).contains("18.4");
    }

    @Test
    void negativeRecentPointsProduceANegativeContribution() {
        List<PlayerGameStats> priorGames = List.of(gameStats(new BigDecimal("-2.00")));

        Optional<FactorResult> result = RecentPerformanceFactorCalculator.calculate(priorGames);

        assertThat(result).isPresent();
        assertThat(result.get().contribution()).isEqualByComparingTo("-1.06");
    }

    @Test
    void emptyOrNullPriorGamesYieldsEmptyOptional() {
        assertThat(RecentPerformanceFactorCalculator.calculate(List.of())).isEmpty();
        assertThat(RecentPerformanceFactorCalculator.calculate(null)).isEmpty();
    }

    @Test
    void missingFantasyPointsOnTheMostRecentGameYieldsEmptyOptional() {
        List<PlayerGameStats> priorGames = List.of(gameStats(null));

        assertThat(RecentPerformanceFactorCalculator.calculate(priorGames)).isEmpty();
    }

    private static PlayerGameStats gameStats(BigDecimal fantasyPointsPpr) {
        return new PlayerGameStats(null, null, null, 5, 4, 40, 0, 0, null, null, null, null, null, 0,
                fantasyPointsPpr, fantasyPointsPpr);
    }
}
