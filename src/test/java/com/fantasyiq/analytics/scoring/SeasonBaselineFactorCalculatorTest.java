package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.PlayerGameStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        assertThat(result.get().narrative()).contains("18.1").contains("3 games").contains("this season");
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
        assertThat(SeasonBaselineFactorCalculator.effectiveGames(priorGames, List.of())).isEqualTo(2);
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
        assertThat(SeasonBaselineFactorCalculator.calculate(List.of(), List.of())).isEmpty();
        assertThat(SeasonBaselineFactorCalculator.calculate(null, null)).isEmpty();
        assertThat(SeasonBaselineFactorCalculator.effectiveGames(null, null)).isZero();
    }

    @Test
    void beforeAnyGamesThisSeasonItIsLastSeasonsAverage() {
        Optional<FactorResult> result = SeasonBaselineFactorCalculator.calculate(List.of(), pointsOf(17, "12.00"));

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("12.00");
        assertThat(result.get().narrative()).contains("12.0").contains("17 games last season").contains("no games yet this season");
    }

    @Test
    void lastSeasonCountsAsFourExtraGamesAgainstThisSeasonsGames() {
        // Two games of 20 against a full last season of 10: (40 + 4 * 10) / (2 + 4) = 13.33
        Optional<FactorResult> result = SeasonBaselineFactorCalculator.calculate(
                List.of(gameStats("20.00"), gameStats("20.00")), pointsOf(17, "10.00"));

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("13.33");
        assertThat(result.get().narrative()).contains("2 games this season").contains("counting as about 4.0 extra games");
    }

    @Test
    void lastSeasonFadesAsThisSeasonsGamesPileUp() {
        BigDecimal early = SeasonBaselineFactorCalculator
                .calculate(thisSeason(2, "20.00"), pointsOf(17, "10.00")).orElseThrow().contribution();
        BigDecimal late = SeasonBaselineFactorCalculator
                .calculate(thisSeason(12, "20.00"), pointsOf(17, "10.00")).orElseThrow().contribution();

        // (240 + 40) / 16 = 17.50: mostly this season by now.
        assertThat(late).isEqualByComparingTo("17.50");
        assertThat(late).isGreaterThan(early);
    }

    @Test
    void aShortLastSeasonCountsForLess() {
        // One game last season: weight 4 * 1 / 17 = 0.235, so it barely moves a baseline of 10.
        Optional<FactorResult> result = SeasonBaselineFactorCalculator.calculate(
                List.of(gameStats("10.00")), pointsOf(1, "30.00"));

        // (10 + 0.2353 * 30) / 1.2353 = 13.81
        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("13.81");
    }

    @Test
    void lastSeasonWeightIsCappedAtASeasonsWorthOfGames() {
        BigDecimal seventeen = SeasonBaselineFactorCalculator
                .calculate(thisSeason(2, "20.00"), pointsOf(17, "10.00")).orElseThrow().contribution();
        BigDecimal twenty = SeasonBaselineFactorCalculator
                .calculate(thisSeason(2, "20.00"), pointsOf(20, "10.00")).orElseThrow().contribution();

        assertThat(twenty).isEqualByComparingTo(seventeen);
    }

    @Test
    void effectiveGamesAddsLastSeasonsWeightToThisSeasonsGames() {
        // 17 games last season = 4 extra games
        assertThat(SeasonBaselineFactorCalculator.effectiveGames(List.of(), pointsOf(17, "10.00"))).isEqualTo(4);
        assertThat(SeasonBaselineFactorCalculator.effectiveGames(thisSeason(2, "20.00"), pointsOf(17, "10.00"))).isEqualTo(6);
        // a one-game last season is worth 0.2 of a game, which does not add up to one
        assertThat(SeasonBaselineFactorCalculator.effectiveGames(List.of(), pointsOf(1, "30.00"))).isZero();
    }

    private static List<PlayerGameStats> thisSeason(int games, String points) {
        return seasonOf(games, points);
    }

    private static List<BigDecimal> pointsOf(int games, String points) {
        return java.util.Collections.nCopies(games, new BigDecimal(points));
    }

    private static List<PlayerGameStats> seasonOf(int games, String points) {
        List<PlayerGameStats> list = new ArrayList<>();
        for (int i = 0; i < games; i++) {
            list.add(gameStats(points));
        }
        return list;
    }

    private static PlayerGameStats gameStats(String fantasyPointsPpr) {
        BigDecimal points = fantasyPointsPpr == null ? null : new BigDecimal(fantasyPointsPpr);
        return new PlayerGameStats(null, null, null, 5, 4, 40, 0, 0, null, null, null, null, null, 0, points, points);
    }
}
