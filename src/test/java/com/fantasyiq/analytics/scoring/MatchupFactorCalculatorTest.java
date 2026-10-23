package com.fantasyiq.analytics.scoring;

import com.fantasyiq.domain.stats.DefenseVsPositionStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MatchupFactorCalculatorTest {

    private static final BigDecimal BASELINE = new BigDecimal("16.00");

    @Test
    void anOpponentThatAllowsMoreThanAverageRaisesTheBaselineByAQuarterOfTheGap() {
        List<DefenseVsPositionStats> opponent = List.of(allowed(30, 1), allowed(30, 2));
        List<DefenseVsPositionStats> league = List.of(allowed(30, 1), allowed(18, 1), allowed(24, 2), allowed(24, 2));

        Optional<FactorResult> result = MatchupFactorCalculator.calculate(opponent, league, BASELINE, "WR");

        assertThat(result).isPresent();
        FactorResult factor = result.get();
        assertThat(factor.factorType()).isEqualTo("MATCHUP");
        // opponent 30.0 vs league 24.0 -> +25% relative; 16.00 * 0.25 * 0.25 = 1.00
        assertThat(factor.factorValue()).isEqualByComparingTo("0.2500");
        assertThat(factor.contribution()).isEqualByComparingTo("1.00");
        assertThat(factor.narrative()).contains("25% more").contains("WR");
    }

    @Test
    void anOpponentThatAllowsLessThanAverageLowersIt() {
        List<DefenseVsPositionStats> opponent = List.of(allowed(18, 1));
        List<DefenseVsPositionStats> league = List.of(allowed(18, 1), allowed(30, 1));

        Optional<FactorResult> result = MatchupFactorCalculator.calculate(opponent, league, BASELINE, "RB");

        // 18 vs 24 -> -25%; 16.00 * 0.25 * -0.25 = -1.00
        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("-1.00");
        assertThat(result.get().narrative()).contains("25% fewer");
    }

    @Test
    void anAverageDefenseChangesNothing() {
        List<DefenseVsPositionStats> opponent = List.of(allowed(24, 1));
        List<DefenseVsPositionStats> league = List.of(allowed(24, 1), allowed(24, 1));

        Optional<FactorResult> result = MatchupFactorCalculator.calculate(opponent, league, BASELINE, "TE");

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void theAdjustmentScalesWithThePlayersBaseline() {
        List<DefenseVsPositionStats> opponent = List.of(allowed(30, 1));
        List<DefenseVsPositionStats> league = List.of(allowed(30, 1), allowed(18, 1));

        BigDecimal star = MatchupFactorCalculator.calculate(opponent, league, new BigDecimal("16.00"), "WR")
                .orElseThrow().contribution();
        BigDecimal depth = MatchupFactorCalculator.calculate(opponent, league, new BigDecimal("4.00"), "WR")
                .orElseThrow().contribution();

        assertThat(star).isEqualByComparingTo(depth.multiply(BigDecimal.valueOf(4)));
    }

    @Test
    void aPlayerWithNoPositiveBaselineIsNotAdjusted() {
        List<DefenseVsPositionStats> opponent = List.of(allowed(30, 1));
        List<DefenseVsPositionStats> league = List.of(allowed(30, 1), allowed(18, 1));

        Optional<FactorResult> result = MatchupFactorCalculator.calculate(opponent, league, new BigDecimal("-2.00"), "QB");

        assertThat(result.orElseThrow().contribution()).isEqualByComparingTo("0.00");
    }

    @Test
    void missingHistoryYieldsEmptyOptional() {
        List<DefenseVsPositionStats> some = List.of(allowed(24, 1));

        assertThat(MatchupFactorCalculator.calculate(List.of(), some, BASELINE, "QB")).isEmpty();
        assertThat(MatchupFactorCalculator.calculate(some, List.of(), BASELINE, "QB")).isEmpty();
        assertThat(MatchupFactorCalculator.calculate(null, some, BASELINE, "QB")).isEmpty();
        assertThat(MatchupFactorCalculator.calculate(some, null, BASELINE, "QB")).isEmpty();
    }

    @Test
    void aLeagueThatAllowedNothingCannotBeUsedAsAReference() {
        List<DefenseVsPositionStats> zero = List.of(allowed(0, 1));

        assertThat(MatchupFactorCalculator.calculate(zero, zero, BASELINE, "QB")).isEmpty();
    }

    private static DefenseVsPositionStats allowed(int fantasyPointsAllowedPpr, int week) {
        return new DefenseVsPositionStats(null, 2026, week, "WR",
                BigDecimal.valueOf(fantasyPointsAllowedPpr), BigDecimal.valueOf(fantasyPointsAllowedPpr), 1, 1);
    }
}
