package com.fantasyiq.analytics.trade;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerNotFoundException;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.stats.InjuryReconciliationService;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real DB fixtures, no vendor to stub -- same reasoning as
 * StartSitRecommendationServiceIT (pure composition over already-existing
 * domain data). Unlike that test, there's no season/week to isolate
 * fixtures by since trade value is season-agnostic; IntegrationTestBase's
 * broad @BeforeEach wipe already guarantees a clean slate per test.
 */
class TradeAnalysisServiceIT extends IntegrationTestBase {

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private PlayerGameStatsRepository playerGameStatsRepository;
    @Autowired
    private InjuryReconciliationService injuryReconciliationService;
    @Autowired
    private TradeAnalysisService tradeAnalysisService;

    private int jerseyCounter = 1;

    @Test
    void positionalReplacementLevelFallsBackToTheLowestScoredPlayerWhenFewerThanTheCutoffExist() {
        // Only 3 WRs exist in this test's fixture data, well under the
        // real replacement rank (24) -- exercises the "fewer players than
        // the cutoff" fallback path deterministically instead of needing
        // 24 real fixtures.
        Player bestWr = wrWithRecentPoints("Best WR", new BigDecimal("30.00"));
        Player midWr = wrWithRecentPoints("Mid WR", new BigDecimal("20.00"));
        wrWithRecentPoints("Worst WR", new BigDecimal("10.00"));

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(bestWr.getId()), List.of(midWr.getId()));

        // 30.00 * 0.53 = 15.90, 20.00 * 0.53 = 10.60, 10.00 * 0.53 = 5.30
        // (RecentPerformanceFactorCalculator's WEIGHT) -- replacement level
        // is the lowest of the three since only 3 WRs exist.
        PlayerTradeValue bestValue = result.sideA().players().get(0);
        assertThat(bestValue.score()).isEqualByComparingTo("15.90");
        assertThat(bestValue.replacementLevel()).isEqualByComparingTo("5.30");
        assertThat(bestValue.valueAboveReplacement()).isEqualByComparingTo("10.60");

        PlayerTradeValue midValue = result.sideB().players().get(0);
        assertThat(midValue.score()).isEqualByComparingTo("10.60");
        assertThat(midValue.valueAboveReplacement()).isEqualByComparingTo("5.30");

        assertThat(result.sideA().totalValue()).isEqualByComparingTo("10.60");
        assertThat(result.sideB().totalValue()).isEqualByComparingTo("5.30");
        assertThat(result.valueDelta()).isEqualByComparingTo("5.30");
    }

    @Test
    void unevenTradeSizesAreSupported() {
        Player p1 = wrWithRecentPoints("Package Player 1", new BigDecimal("12.00"));
        Player p2 = wrWithRecentPoints("Package Player 2", new BigDecimal("8.00"));
        Player p3 = wrWithRecentPoints("Solo Player", new BigDecimal("15.00"));

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(p1.getId(), p2.getId()), List.of(p3.getId()));

        assertThat(result.sideA().players()).hasSize(2);
        assertThat(result.sideB().players()).hasSize(1);
    }

    @Test
    void playersWithInsufficientDataAreListedWithNullValueNotOmitted() {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Player noDataPlayer = playerRepository.save(
                new Player("No Data WR", "WR", ari, 99, "ACTIVE", LocalDate.of(1998, 1, 1)));
        Player withDataPlayer = wrWithRecentPoints("Has Data WR", new BigDecimal("10.00"));

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(noDataPlayer.getId()), List.of(withDataPlayer.getId()));

        PlayerTradeValue noDataValue = result.sideA().players().get(0);
        assertThat(noDataValue.score()).isNull();
        assertThat(noDataValue.valueAboveReplacement()).isNull();
        assertThat(result.sideA().totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void injuryStatusReducesValueViaTheSameInjuryFactorCalculatorStartSitUses() {
        Player healthyWr = wrWithRecentPoints("Healthy WR", new BigDecimal("10.00"));
        Player outWr = wrWithRecentPoints("Out WR", new BigDecimal("10.00"));
        injuryReconciliationService.resolveOrCreateFromEspn(outWr, "OUT", LocalDate.now());

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(healthyWr.getId()), List.of(outWr.getId()));

        PlayerTradeValue outValue = result.sideB().players().get(0);
        // Same -1000 hard-override contribution InjuryFactorCalculator
        // applies for start/sit -- proves real composition, not a
        // reimplementation of injury handling for trade value.
        assertThat(outValue.score()).isLessThan(new BigDecimal("-900"));
    }

    @Test
    void unknownPlayerIdThrowsPlayerNotFoundException() {
        Player realPlayer = wrWithRecentPoints("Real Player", new BigDecimal("10.00"));
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> tradeAnalysisService.analyze(List.of(unknownId), List.of(realPlayer.getId())))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    private Player wrWithRecentPoints(String name, BigDecimal fantasyPointsPpr) {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Team sf = teamRepository.findByAbbreviation("SF").orElseThrow();
        Player player = playerRepository.save(new Player(name, "WR", ari, nextJerseyNumber(), "ACTIVE", LocalDate.of(1998, 1, 1)));
        Game game = gameRepository.save(new Game(
                "trade-it-" + UUID.randomUUID(), 2094, 1, ari, sf, Instant.parse("2094-09-07T17:00:00Z"),
                "Test Stadium", "FINAL"));
        playerGameStatsRepository.save(new PlayerGameStats(player, game, ari, 5, 4, 40, 0, 0, null, null, null, null,
                null, 0, fantasyPointsPpr, fantasyPointsPpr));
        return player;
    }

    private int nextJerseyNumber() {
        return jerseyCounter++;
    }
}
