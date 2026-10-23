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
 * domain data). A trade is valued against the most recent season that has any
 * box score, so there's no season/week to isolate fixtures by;
 * IntegrationTestBase's broad @BeforeEach wipe already guarantees a clean
 * slate per test.
 */
class TradeAnalysisServiceIT extends IntegrationTestBase {

    private static final int SEASON = 2094;

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
        Player bestWr = wrWithSeason("Best WR", SEASON, 30.00);
        Player midWr = wrWithSeason("Mid WR", SEASON, 20.00);
        wrWithSeason("Worst WR", SEASON, 10.00);

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(bestWr.getId()), List.of(midWr.getId()));

        // A one-game season average is that game; replacement level is the
        // lowest of the three since only 3 WRs exist.
        PlayerTradeValue bestValue = result.sideA().players().get(0);
        assertThat(bestValue.score()).isEqualByComparingTo("30.00");
        assertThat(bestValue.replacementLevel()).isEqualByComparingTo("10.00");
        assertThat(bestValue.valueAboveReplacement()).isEqualByComparingTo("20.00");

        PlayerTradeValue midValue = result.sideB().players().get(0);
        assertThat(midValue.score()).isEqualByComparingTo("20.00");
        assertThat(midValue.valueAboveReplacement()).isEqualByComparingTo("10.00");

        assertThat(result.sideA().totalValue()).isEqualByComparingTo("20.00");
        assertThat(result.sideB().totalValue()).isEqualByComparingTo("10.00");
        assertThat(result.valueDelta()).isEqualByComparingTo("10.00");
    }

    @Test
    void aPlayersValueIsHisSeasonAverageNotJustHisLastGame() {
        Player steady = wrWithSeason("Steady WR", SEASON, 10.00, 20.00, 30.00);
        Player lastGameSpike = wrWithSeason("Spike WR", SEASON, 2.00, 2.00, 26.00);

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(steady.getId()), List.of(lastGameSpike.getId()));

        assertThat(result.sideA().players().get(0).score()).isEqualByComparingTo("20.00");
        // (2 + 2 + 26) / 3 -- the 26 in his last game does not make him worth 26.
        assertThat(result.sideB().players().get(0).score()).isEqualByComparingTo("10.00");
    }

    @Test
    void onlyTheMostRecentSeasonCounts() {
        Player twoSeasons = wrWithSeason("Two Seasons WR", SEASON - 1, 40.00, 40.00);
        addSeason(twoSeasons, SEASON, 10.00);
        Player other = wrWithSeason("Other WR", SEASON, 10.00);

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(twoSeasons.getId()), List.of(other.getId()));

        assertThat(result.sideA().players().get(0).score()).isEqualByComparingTo("10.00");
    }

    @Test
    void aPlayerWithNoGamesInTheLatestSeasonHasNoValueNotAnOldOne() {
        Player lastSeasonOnly = wrWithSeason("Last Season Only WR", SEASON - 1, 25.00);
        Player current = wrWithSeason("Current WR", SEASON, 10.00);

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(lastSeasonOnly.getId()), List.of(current.getId()));

        PlayerTradeValue noValue = result.sideA().players().get(0);
        assertThat(noValue.score()).isNull();
        assertThat(noValue.valueAboveReplacement()).isNull();
        assertThat(result.sideA().totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unevenTradeSizesAreSupported() {
        Player p1 = wrWithSeason("Package Player 1", SEASON, 12.00);
        Player p2 = wrWithSeason("Package Player 2", SEASON, 8.00);
        Player p3 = wrWithSeason("Solo Player", SEASON, 15.00);

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
        Player withDataPlayer = wrWithSeason("Has Data WR", SEASON, 10.00);

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(noDataPlayer.getId()), List.of(withDataPlayer.getId()));

        PlayerTradeValue noDataValue = result.sideA().players().get(0);
        assertThat(noDataValue.score()).isNull();
        assertThat(noDataValue.valueAboveReplacement()).isNull();
        assertThat(result.sideA().totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aPlayerListedOutIsDiscountedForAShortAbsenceNotWrittenOff() {
        Player healthyWr = wrWithSeason("Healthy WR", SEASON, 20.00);
        Player outWr = wrWithSeason("Out WR", SEASON, 20.00);
        injuryReconciliationService.resolveOrCreateFromEspn(outWr, "OUT", LocalDate.now());

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(healthyWr.getId()), List.of(outWr.getId()));

        PlayerTradeValue outValue = result.sideB().players().get(0);
        // 15% of the remaining season, where v1 applied a flat -1000.
        assertThat(outValue.score()).isEqualByComparingTo("17.00");
        assertThat(outValue.factors()).extracting(factor -> factor.factorType()).containsExactly("BASELINE", "INJURY");
        assertThat(result.sideA().players().get(0).score()).isEqualByComparingTo("20.00");
    }

    @Test
    void injuredReserveCostsMoreThanOut() {
        Player irWr = wrWithSeason("IR WR", SEASON, 20.00);
        Player outWr = wrWithSeason("Short Out WR", SEASON, 20.00);
        // ESPN's real wording, not the abbreviation
        injuryReconciliationService.resolveOrCreateFromEspn(irWr, "INJURED RESERVE", LocalDate.now());
        injuryReconciliationService.resolveOrCreateFromEspn(outWr, "OUT", LocalDate.now());

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(irWr.getId()), List.of(outWr.getId()));

        assertThat(result.sideA().players().get(0).score()).isEqualByComparingTo("10.00");
        assertThat(result.sideA().players().get(0).score()).isLessThan(result.sideB().players().get(0).score());
    }

    @Test
    void theLatestReportPerPlayerIsTheOneThatCounts() {
        Player recovered = wrWithSeason("Recovered WR", SEASON, 20.00);
        Player other = wrWithSeason("Other Healthy WR", SEASON, 20.00);
        injuryReconciliationService.resolveOrCreateFromEspn(recovered, "OUT", LocalDate.now().minusDays(7));
        injuryReconciliationService.resolveOrCreateFromEspn(recovered, "ACTIVE", LocalDate.now());

        TradeAnalysisResult result = tradeAnalysisService.analyze(
                List.of(recovered.getId()), List.of(other.getId()));

        assertThat(result.sideA().players().get(0).score()).isEqualByComparingTo("20.00");
        assertThat(result.sideA().players().get(0).factors()).hasSize(1);
    }

    @Test
    void unknownPlayerIdThrowsPlayerNotFoundException() {
        Player realPlayer = wrWithSeason("Real Player", SEASON, 10.00);
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> tradeAnalysisService.analyze(List.of(unknownId), List.of(realPlayer.getId())))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    private Player wrWithSeason(String name, int season, double... pprByWeek) {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Player player = playerRepository.save(new Player(name, "WR", ari, nextJerseyNumber(), "ACTIVE", LocalDate.of(1998, 1, 1)));
        addSeason(player, season, pprByWeek);
        return player;
    }

    private void addSeason(Player player, int season, double... pprByWeek) {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Team sf = teamRepository.findByAbbreviation("SF").orElseThrow();
        for (int week = 1; week <= pprByWeek.length; week++) {
            Game game = gameRepository.save(new Game(
                    "trade-it-" + UUID.randomUUID(), season, week, ari, sf, Instant.parse(season + "-09-07T17:00:00Z"),
                    "Test Stadium", "FINAL"));
            BigDecimal ppr = BigDecimal.valueOf(pprByWeek[week - 1]);
            playerGameStatsRepository.save(new PlayerGameStats(player, game, ari, 5, 4, 40, 0, 0, null, null, null, null,
                    null, 0, ppr, ppr));
        }
    }

    private int nextJerseyNumber() {
        return jerseyCounter++;
    }
}
