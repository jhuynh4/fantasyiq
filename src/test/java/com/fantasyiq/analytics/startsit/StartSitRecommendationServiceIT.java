package com.fantasyiq.analytics.startsit;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.cache.RecommendationCacheService;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationFactor;
import com.fantasyiq.domain.recommendation.RecommendationReconciliationService;
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
    private static final int TARGET_WEEK = 8;

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
    private RecommendationReconciliationService recommendationReconciliationService;
    @Autowired
    private StartSitRecommendationService startSitRecommendationService;
    @Autowired
    private RecommendationCacheService recommendationCacheService;

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
    void composesAllAvailableFactorsIntoAnExpectedPointsScore() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Team dal = team("DAL");
        Player wr = player("Full Data WR", "WR", ari, 11);

        // Season so far: 10, 12, 8, 14 PPR -> BASELINE 11.00 over 4 games.
        playedGames(wr, ari, sf, 10, 12, 8, 14);

        // SF has allowed 30 to WRs every week and DAL 18, so the league
        // average is 24 and SF is 25% softer than average.
        for (int week = 1; week <= 4; week++) {
            defenseVsPositionStatsRepository.save(new DefenseVsPositionStats(sf, SEASON, week, "WR",
                    BigDecimal.valueOf(30), BigDecimal.valueOf(30), 32, 32));
            defenseVsPositionStatsRepository.save(new DefenseVsPositionStats(dal, SEASON, week, "WR",
                    BigDecimal.valueOf(18), BigDecimal.valueOf(18), 1, 1));
        }

        Game targetGame = saveGame("target-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));
        bettingLineReconciliationService.resolveOrCreate(targetGame, ari, new BigDecimal("26.0"),
                new BigDecimal("-3.5"), new BigDecimal("45.5"), "draftkings");
        weatherForecastReconciliationService.resolveOrCreate(targetGame, 60, 25, 10, "windy");
        injuryReconciliationService.resolveOrCreateFromEspn(wr, "QUESTIONABLE", LocalDate.of(2099, 9, 5));

        int recommendationsGenerated = startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        assertThat(recommendationsGenerated).isEqualTo(1);
        Recommendation recommendation = recommendationFor(wr);
        assertThat(recommendation.getScoringVersion()).isEqualTo("v2.1");

        List<RecommendationFactor> factors = recommendation.getFactors();
        assertThat(factors).extracting(RecommendationFactor::getFactorType)
                .containsExactlyInAnyOrder("BASELINE", "MATCHUP", "VEGAS", "WEATHER", "INJURY");
        assertThat(contributionOf(factors, "BASELINE")).isEqualByComparingTo("11.00");
        // 11.00 * 0.25 * 0.25
        assertThat(contributionOf(factors, "MATCHUP")).isEqualByComparingTo("0.69");
        // 11.00 * 0.25 * (26.0 / 22.5 - 1)
        assertThat(contributionOf(factors, "VEGAS")).isEqualByComparingTo("0.43");
        // (25 - 15) mph * 1% of 11.00
        assertThat(contributionOf(factors, "WEATHER")).isEqualByComparingTo("-1.10");
        // Questionable: 15% of 11.00
        assertThat(contributionOf(factors, "INJURY")).isEqualByComparingTo("-1.65");

        // Score is exactly the sum of its factor contributions -- the
        // "explanation and the score are the same computation" invariant.
        BigDecimal expectedScore = factors.stream()
                .map(RecommendationFactor::getContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(recommendation.getScore()).isEqualByComparingTo(expectedScore);
        assertThat(recommendation.getScore()).isEqualByComparingTo("9.37");
    }

    @Test
    void aPlayerWithNoGamesThisSeasonGetsNoRecommendationEvenWithOtherData() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player rookie = player("No Games WR", "WR", ari, 12);
        Game targetGame = saveGame("no-games-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));
        bettingLineReconciliationService.resolveOrCreate(targetGame, ari, new BigDecimal("26.0"),
                new BigDecimal("-3.5"), new BigDecimal("45.5"), "draftkings");
        injuryReconciliationService.resolveOrCreateFromEspn(rookie, "QUESTIONABLE", LocalDate.of(2099, 9, 5));

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        assertThat(recommendationRepository.findByPlayerAndSeasonAndWeekAndType(rookie, SEASON, TARGET_WEEK, "START_SIT"))
                .isEmpty();
    }

    @Test
    void aSeasonLongStarterOutranksABackupWhoHadOneBigGame() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player starter = player("Steady Starter QB", "QB", ari, 13);
        Player backup = player("One Big Game QB", "QB", ari, 14);
        playedGames(starter, ari, sf, 18, 18, 18, 18);
        // v1 scored the backup on his last game and his jump in volume; averaging the season does not.
        playedGames(backup, ari, sf, 0, 0, 2, 26);
        saveGame("target-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        assertThat(recommendationFor(starter).getScore()).isEqualByComparingTo("18.00");
        assertThat(recommendationFor(backup).getScore()).isEqualByComparingTo("7.00");
        assertThat(recommendationFor(starter).getScore()).isGreaterThan(recommendationFor(backup).getScore());
    }

    @Test
    void aPlayerWithNoGamesYetThisSeasonIsScoredFromLastSeason() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player veteran = player("Week One Veteran WR", "WR", ari, 30);
        playedLastSeason(veteran, ari, sf, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14);
        saveGame("week-one-game", ari, sf, 1, Instant.parse("2099-09-07T17:00:00Z"));

        startSitRecommendationService.computeForWeek(SEASON, 1);

        Recommendation recommendation = recommendationRepository
                .findByPlayerAndSeasonAndWeekAndType(veteran, SEASON, 1, "START_SIT").orElseThrow();
        assertThat(recommendation.getScore()).isEqualByComparingTo("14.00");
        assertThat(contributionOf(recommendation.getFactors(), "BASELINE")).isEqualByComparingTo("14.00");
        // 17 games last season count as 4 games of evidence
        assertThat(recommendation.getConfidence()).isEqualTo("MEDIUM");
    }

    @Test
    void aRookieWithNoGamesAnywhereStillGetsNoRowInWeekOne() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player rookie = player("Week One Rookie WR", "WR", ari, 31);
        saveGame("week-one-game", ari, sf, 1, Instant.parse("2099-09-07T17:00:00Z"));

        startSitRecommendationService.computeForWeek(SEASON, 1);

        assertThat(recommendationRepository.findByPlayerAndSeasonAndWeekAndType(rookie, SEASON, 1, "START_SIT")).isEmpty();
    }

    @Test
    void lastSeasonBlendsWithThisSeasonsGamesAndFadesAsTheyPileUp() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player player = player("Blended WR", "WR", ari, 32);
        playedLastSeason(player, ari, sf, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
        playedGames(player, ari, sf, 20, 20);
        saveGame("target-game", ari, sf, 3, Instant.parse("2099-09-07T17:00:00Z"));

        startSitRecommendationService.computeForWeek(SEASON, 3);

        // (2 * 20 + 4 * 10) / (2 + 4) = 13.33, between last season's 10 and this season's 20.
        Recommendation recommendation = recommendationRepository
                .findByPlayerAndSeasonAndWeekAndType(player, SEASON, 3, "START_SIT").orElseThrow();
        assertThat(recommendation.getScore()).isEqualByComparingTo("13.33");
        assertThat(recommendation.getConfidence()).isEqualTo("HIGH");
    }

    @Test
    void aMissingFactorLeavesTheScoreAtTheBaselineInsteadOfLoweringIt() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player withLine = player("Has Odds WR", "WR", ari, 15);
        Player withoutLine = player("No Odds WR", "WR", team("DAL"), 16);
        playedGames(withLine, ari, sf, 12, 12);
        playedGames(withoutLine, team("DAL"), sf, 12, 12);
        Game targetGame = saveGame("target-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));
        saveGame("other-game", team("DAL"), sf, TARGET_WEEK, Instant.parse("2099-09-07T20:00:00Z"));
        // A typical total: the market has no opinion either way.
        bettingLineReconciliationService.resolveOrCreate(targetGame, ari, new BigDecimal("22.5"),
                new BigDecimal("-3.5"), new BigDecimal("45.5"), "draftkings");

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        assertThat(recommendationFor(withLine).getScore()).isEqualByComparingTo("12.00");
        assertThat(recommendationFor(withoutLine).getScore()).isEqualByComparingTo("12.00");
    }

    @Test
    void confidenceReflectsHowManyGamesTheBaselineRestsOn() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player veteran = player("Six Games WR", "WR", ari, 17);
        Player middling = player("Three Games WR", "WR", ari, 18);
        Player newcomer = player("One Game WR", "WR", ari, 19);
        playedGames(veteran, ari, sf, 10, 10, 10, 10, 10, 10);
        playedGames(middling, ari, sf, 10, 10, 10);
        playedGames(newcomer, ari, sf, 10);
        saveGame("target-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        assertThat(recommendationFor(veteran).getConfidence()).isEqualTo("HIGH");
        assertThat(recommendationFor(middling).getConfidence()).isEqualTo("MEDIUM");
        assertThat(recommendationFor(newcomer).getConfidence()).isEqualTo("LOW");
    }

    @Test
    void aPlayerRuledOutIsAlwaysLowConfidenceAndScoresBelowEveryHealthyPlayer() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player ruledOut = player("Ruled Out WR", "WR", ari, 20);
        Player healthy = player("Healthy WR", "WR", ari, 21);
        playedGames(ruledOut, ari, sf, 25, 25, 25, 25, 25, 25, 25);
        playedGames(healthy, ari, sf, 3, 3);
        injuryReconciliationService.resolveOrCreateFromEspn(ruledOut, "OUT", LocalDate.of(2099, 9, 5));
        saveGame("target-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        Recommendation out = recommendationFor(ruledOut);
        assertThat(out.getConfidence()).isEqualTo("LOW");
        assertThat(out.getScore()).isLessThan(recommendationFor(healthy).getScore());
    }

    @Test
    void aRecommendationTheEngineNoLongerProducesIsRemovedFromTheWeek() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player current = player("Still Scored WR", "WR", ari, 24);
        Player leftover = player("Leftover From Older Version WR", "WR", ari, 25);
        playedGames(current, ari, sf, 10, 12);
        saveGame("target-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));
        // A row from an earlier scoring version for a player with no games this season,
        // which v2 would never produce.
        recommendationReconciliationService.resolveOrCreate(leftover, SEASON, TARGET_WEEK, "START_SIT",
                new BigDecimal("12.000"), "LOW", "v1");
        assertThat(recommendationRepository.findByPlayerAndSeasonAndWeekAndType(leftover, SEASON, TARGET_WEEK, "START_SIT"))
                .isPresent();

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        assertThat(recommendationRepository.findByPlayerAndSeasonAndWeekAndType(leftover, SEASON, TARGET_WEEK, "START_SIT"))
                .isEmpty();
        assertThat(recommendationFor(current).getScoringVersion()).isEqualTo("v2.1");
        assertThat(recommendationCacheService.getStartSit(SEASON, TARGET_WEEK, null))
                .extracting(snapshot -> snapshot.playerName())
                .containsExactly("Still Scored WR");
    }

    @Test
    void runningTwiceUpdatesInPlaceRatherThanDuplicating() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player wr = player("Idempotent WR", "WR", ari, 22);
        playedGames(wr, ari, sf, 9, 11);
        Game targetGame = saveGame("idempotent-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));
        bettingLineReconciliationService.resolveOrCreate(targetGame, ari, new BigDecimal("26.0"),
                new BigDecimal("-3.5"), new BigDecimal("45.5"), "draftkings");

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);
        long afterFirstRun = recommendationRepository.count();

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);
        long afterSecondRun = recommendationRepository.count();

        assertThat(afterSecondRun).isEqualTo(afterFirstRun);
        assertThat(recommendationFor(wr).getFactors()).hasSize(2);
    }

    @Test
    void computeForWeekPopulatesTheCacheWithoutWaitingForARead() {
        Team ari = team("ARI");
        Team sf = team("SF");
        Player wr = player("Cache Warm WR", "WR", ari, 23);
        playedGames(wr, ari, sf, 10);
        saveGame("cache-warm-game", ari, sf, TARGET_WEEK, Instant.parse("2099-09-07T17:00:00Z"));

        startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);

        // Deleting straight from the DB, bypassing refreshStartSit entirely --
        // if the cache still has a row, computeForWeek must have populated it.
        recommendationRepository.deleteAll();
        assertThat(recommendationCacheService.getStartSit(SEASON, TARGET_WEEK, null)).hasSize(1);
    }

    private Team team(String abbreviation) {
        return teamRepository.findByAbbreviation(abbreviation).orElseThrow();
    }

    private Player player(String name, String position, Team team, int jersey) {
        return playerRepository.save(new Player(name, position, team, jersey, "ACTIVE", LocalDate.of(1997, 1, 1)));
    }

    private Game saveGame(String externalRef, Team home, Team away, int week, Instant kickoff) {
        return gameRepository.save(new Game(externalRef, SEASON, week, home, away, kickoff, "Test Stadium", "SCHEDULED"));
    }

    /** One finished game per listed score, in weeks 1..n, each with its own game row. */
    private void playedGames(Player player, Team home, Team away, double... pprByWeek) {
        for (int week = 1; week <= pprByWeek.length; week++) {
            Game game = saveGame("history-" + player.getFullName() + "-" + week, home, away, week,
                    Instant.parse("2099-0" + week + "-07T17:00:00Z"));
            BigDecimal ppr = BigDecimal.valueOf(pprByWeek[week - 1]);
            playerGameStatsRepository.save(new PlayerGameStats(player, game, home,
                    5, 4, 40, 0, 0, null, null, null, null, null, 0, ppr, ppr));
        }
    }

    /** A finished season the year before the one being scored: one game per listed score, in weeks 1..n. */
    private void playedLastSeason(Player player, Team home, Team away, double... pprByWeek) {
        for (int week = 1; week <= pprByWeek.length; week++) {
            Game game = gameRepository.save(new Game("last-season-" + player.getFullName() + "-" + week, SEASON - 1, week,
                    home, away, Instant.parse("2098-0" + (week % 9 + 1) + "-07T17:00:00Z"), "Test Stadium", "FINAL"));
            BigDecimal ppr = BigDecimal.valueOf(pprByWeek[week - 1]);
            playerGameStatsRepository.save(new PlayerGameStats(player, game, home,
                    5, 4, 40, 0, 0, null, null, null, null, null, 0, ppr, ppr));
        }
    }

    private Recommendation recommendationFor(Player player) {
        return recommendationRepository
                .findByPlayerAndSeasonAndWeekAndType(player, SEASON, TARGET_WEEK, "START_SIT")
                .orElseThrow();
    }

    private static BigDecimal contributionOf(List<RecommendationFactor> factors, String factorType) {
        return factors.stream()
                .filter(f -> f.getFactorType().equals(factorType))
                .findFirst().orElseThrow()
                .getContribution();
    }
}
