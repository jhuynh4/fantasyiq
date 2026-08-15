package com.fantasyiq.analytics.startsit;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationFactor;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.stats.BettingLineReconciliationService;
import com.fantasyiq.domain.stats.BettingLineRepository;
import com.fantasyiq.domain.stats.DefenseVsPositionStatsRepository;
import com.fantasyiq.domain.stats.DefenseVsPositionStats;
import com.fantasyiq.domain.stats.InjuryReconciliationService;
import com.fantasyiq.domain.stats.InjuryReportRepository;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.WeatherForecastReconciliationService;
import com.fantasyiq.domain.stats.WeatherForecastRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real DB fixtures rather than a stub, same reasoning as
 * DefenseVsPositionStatsServiceIT -- this service is pure composition over
 * already-existing domain data, not a vendor adapter, so there's no
 * response to fake. Uses a distinctive fake season (2099) and wipes the
 * shared mutable tables in @BeforeEach (same broad-cleanup pattern
 * DefenseVsPositionStatsServiceIT already established) rather than trying
 * to scope cleanup narrowly, since players aren't season-scoped.
 */
class StartSitRecommendationServiceIT extends IntegrationTestBase {

    private static final int SEASON = 2099;
    private static final int TARGET_WEEK = 5;

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private PlayerGameStatsRepository playerGameStatsRepository;
    @Autowired
    private DefenseVsPositionStatsRepository defenseVsPositionStatsRepository;
    @Autowired
    private BettingLineReconciliationService bettingLineReconciliationService;
    @Autowired
    private BettingLineRepository bettingLineRepository;
    @Autowired
    private WeatherForecastReconciliationService weatherForecastReconciliationService;
    @Autowired
    private WeatherForecastRepository weatherForecastRepository;
    @Autowired
    private InjuryReconciliationService injuryReconciliationService;
    @Autowired
    private InjuryReportRepository injuryReportRepository;
    @Autowired
    private RecommendationRepository recommendationRepository;
    @Autowired
    private StartSitRecommendationService startSitRecommendationService;

    @BeforeEach
    void cleanUp() {
        recommendationRepository.deleteAll();
        injuryReportRepository.deleteAll();
        bettingLineRepository.deleteAll();
        weatherForecastRepository.deleteAll();
        defenseVsPositionStatsRepository.deleteAll();
        playerGameStatsRepository.deleteAll();
        gameRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void composesAllAvailableFactorsIntoAScoreForAPlayerWithFullData() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player wr = playerRepository.save(new Player("Full Data WR", "WR", ari, 11, "ACTIVE", LocalDate.of(1997, 1, 1)));

        // Prior weeks 1-4: defense matchup history for SF vs WR, and the
        // player's own usage trend (rising targets)
        int[] priorTargets = {5, 6, 10, 12};
        int[] priorDefenseRanks = {18, 20, 24, 26};
        for (int week = 1; week <= 4; week++) {
            Game historyGame = saveGame("history-" + week, ari, sf, week, Instant.parse("2099-0" + week + "-07T17:00:00Z"));
            playerGameStatsRepository.save(new PlayerGameStats(wr, historyGame, ari,
                    priorTargets[week - 1], priorTargets[week - 1] - 1, 40, 0, 0, null, null, null, null, null, 0,
                    BigDecimal.TEN, BigDecimal.TEN));
            defenseVsPositionStatsRepository.save(new DefenseVsPositionStats(sf, SEASON, week, "WR",
                    BigDecimal.valueOf(20), BigDecimal.valueOf(18), priorDefenseRanks[week - 1], priorDefenseRanks[week - 1]));
        }

        Game targetGame = saveGame("target-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));
        bettingLineReconciliationService.resolveOrCreate(targetGame, ari, new BigDecimal("26.0"),
                new BigDecimal("-3.5"), new BigDecimal("45.5"), "draftkings");
        weatherForecastReconciliationService.resolveOrCreate(targetGame, 60, 25, 10, "windy");
        injuryReconciliationService.resolveOrCreateFromEspn(wr, "QUESTIONABLE", LocalDate.of(2099, 9, 5));

        int recommendationsGenerated = startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        assertThat(recommendationsGenerated).isGreaterThanOrEqualTo(1);

        Recommendation recommendation = recommendationRepository
                .findByPlayerAndSeasonAndWeekAndType(wr, SEASON, TARGET_WEEK, "START_SIT")
                .orElseThrow();

        List<RecommendationFactor> factors = recommendation.getFactors();
        assertThat(factors).hasSize(6);
        assertThat(factors).extracting(RecommendationFactor::getFactorType)
                .containsExactlyInAnyOrder("MATCHUP", "VEGAS", "WEATHER", "INJURY", "USAGE", "RECENT_PERFORMANCE");

        // Score is exactly the sum of its factor contributions -- the
        // "explanation and the score are the same computation" invariant.
        BigDecimal expectedScore = factors.stream()
                .map(RecommendationFactor::getContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(recommendation.getScore()).isEqualByComparingTo(expectedScore);

        Optional<RecommendationFactor> injuryFactor = factors.stream()
                .filter(f -> f.getFactorType().equals("INJURY")).findFirst();
        assertThat(injuryFactor).isPresent();
        assertThat(injuryFactor.get().getContribution()).isEqualByComparingTo("-10");
    }

    @Test
    void playersWithNoAvailableDataGetNoRecommendationRow() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player noDataWr = playerRepository.save(new Player("No Data WR", "WR", ari, 12, "ACTIVE", LocalDate.of(1997, 1, 1)));
        saveGame("no-data-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        assertThat(recommendationRepository.findByPlayerAndSeasonAndWeekAndType(noDataWr, SEASON, TARGET_WEEK, "START_SIT"))
                .isEmpty();
    }

    @Test
    void runningTwiceUpdatesInPlaceRatherThanDuplicating() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player wr = playerRepository.save(new Player("Idempotent WR", "WR", ari, 13, "ACTIVE", LocalDate.of(1997, 1, 1)));
        Game targetGame = saveGame("idempotent-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));
        bettingLineReconciliationService.resolveOrCreate(targetGame, ari, new BigDecimal("26.0"),
                new BigDecimal("-3.5"), new BigDecimal("45.5"), "draftkings");

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);
        long afterFirstRun = recommendationRepository.count();

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);
        long afterSecondRun = recommendationRepository.count();

        assertThat(afterSecondRun).isEqualTo(afterFirstRun);

        Recommendation recommendation = recommendationRepository
                .findByPlayerAndSeasonAndWeekAndType(wr, SEASON, TARGET_WEEK, "START_SIT")
                .orElseThrow();
        assertThat(recommendation.getFactors()).hasSize(1);
    }

    private Team team(String abbreviation) {
        return teamRepository.findByAbbreviation(abbreviation).orElseThrow();
    }

    private Game saveGame(String externalRef, Team home, Team away, int week, Instant kickoff) {
        return gameRepository.save(new Game(externalRef, SEASON, week, home, away, kickoff, "Test Stadium", "SCHEDULED"));
    }
}
