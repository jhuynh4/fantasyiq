package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.PlayerGameStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonBaselineFactorCalculatorTest {

    @Test
    void averagesEveryGamePlayedThisSeason() {
        List<PlayerGameStats> priorGames = List.of(
                gameStats("18.40"), gameStats("6.00"), gameStats("30.00"));

        Optional<FactorResult> result = SeasonBaselineFactorCalculator.calculate(priorGames);

        assertThat(result).isPresent();
        assertThat(result.get().factorType()).isEqualTo("BASELINE");
        // (18.40 + 6.00 + 30.00) / 3 = 18.1333 -> 18.13
        assertThat(result.get().contribution()).isEqualByComparingTo("18.13");
        assertThat(result.get().factorValue()).isEqualByComparingTo("18.13");
        assertThat(result.get().narrative()).contains("18.1").contains("3 games");
    }

    @Test
    void aSingleOutlierGameDoesNotDominateTheWayTheLastGameAloneWould() {
        // v1's RECENT_PERFORMANCE would have scored this player on the 40.00 alone.
        List<PlayerGameStats> priorGames = List.of(
                gameStats("40.00"), gameStats("8.00"), gameStats("9.00"), gameStats("7.00"));

        Optional<FactorResult> result = SeasonBaselineFactorCalculator.calculate(priorGames);

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("16.00");
    }

    @Test
    void worksFromASingleGame() {
        Optional<FactorResult> result = SeasonBaselineFactorCalculator.calculate(List.of(gameStats("12.50")));

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("12.50");
        assertThat(result.get().narrative()).contains("1 game ").doesNotContain("1 games");
    }

    @Test
    void gamesWithoutFantasyPointsAreIgnored() {
        List<PlayerGameStats> priorGames = Arrays.asList(gameStats("10.00"), gameStats(null), gameStats("20.00"));

        Optional<FactorResult> result = SeasonBaselineFactorCalculator.calculate(priorGames);

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("15.00");
        assertThat(SeasonBaselineFactorCalculator.gamesCounted(priorGames)).isEqualTo(2);
    }

    @Test
    void negativeAveragesAreKept() {
        Optional<FactorResult> result = SeasonBaselineFactorCalculator.calculate(List.of(gameStats("-2.00")));

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("-2.00");
    }

    @Test
    void noUsableGamesYieldsEmptyOptional() {
        assertThat(SeasonBaselineFactorCalculator.calculate(List.of())).isEmpty();
        assertThat(SeasonBaselineFactorCalculator.calculate(null)).isEmpty();
        assertThat(SeasonBaselineFactorCalculator.calculate(Arrays.asList(gameStats(null)))).isEmpty();
        assertThat(SeasonBaselineFactorCalculator.gamesCounted(null)).isZero();
    }

    private static PlayerGameStats gameStats(String fantasyPointsPpr) {
        BigDecimal points = fantasyPointsPpr == null ? null : new BigDecimal(fantasyPointsPpr);
        return new PlayerGameStats(null, null, null, 5, 4, 40, 0, 0, null, null, null, null, null, 0, points, points);
    }
}
