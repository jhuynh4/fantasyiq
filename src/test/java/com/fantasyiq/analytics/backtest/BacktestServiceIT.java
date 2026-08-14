package com.fantasyiq.analytics.backtest;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.stats.BettingLineRepository;
import com.fantasyiq.domain.stats.DefenseVsPositionStats;
import com.fantasyiq.domain.stats.DefenseVsPositionStatsRepository;
import com.fantasyiq.domain.stats.InjuryReconciliationService;
import com.fantasyiq.domain.stats.InjuryReportRepository;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.domain.stats.WeatherForecastRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Real DB fixtures, same reasoning as StartSitRecommendationServiceIT --
 * this service composes already-existing domain data plus the (already
 * separately-tested) scoring engine, no vendor to stub. Deliberately keeps
 * each fixture player down to a single factor (MATCHUP) so the predicted
 * score is fully attributable to one known input, making the resulting
 * correlation a deterministic, assertable number rather than an opaque one.
 */
class BacktestServiceIT extends IntegrationTestBase {

    private static final int SEASON = 2097;
    private static final int PRIOR_WEEK = 1;
    private static final int TARGET_WEEK = 2;

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
    private BettingLineRepository bettingLineRepository;
    @Autowired
    private WeatherForecastRepository weatherForecastRepository;
    @Autowired
    private InjuryReconciliationService injuryReconciliationService;
    @Autowired
    private InjuryReportRepository injuryReportRepository;
    @Autowired
    private RecommendationRepository recommendationRepository;
    @Autowired
    private BacktestService backtestService;

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
    void correlatesPredictedScoresWithActualPointsAndExcludesInjuryOverrides() {
        Team ari = team("ARI");
        Team sf = team("SF");

        // Prior week: SF's matchup history vs WR (tough, rank 10) and RB
        // (easy, rank 25) -- these are the only factor either fixture
        // player will have, so each predicted score is fully attributable
        // to this one number.
        defenseVsPositionStatsRepository.save(new DefenseVsPositionStats(sf, SEASON, PRIOR_WEEK, "WR",
                BigDecimal.valueOf(30), BigDecimal.valueOf(30), 10, 10));
        defenseVsPositionStatsRepository.save(new DefenseVsPositionStats(sf, SEASON, PRIOR_WEEK, "RB",
                BigDecimal.valueOf(15), BigDecimal.valueOf(15), 25, 25));

        Game targetGame = gameRepository.save(new Game("backtest-target", SEASON, TARGET_WEEK, ari, sf,
                Instant.parse("2097-09-07T17:00:00Z"), "Test Stadium", "FINAL"));

        // Tougher matchup (lower contribution) paired with the lower actual
        // total, easier matchup (higher contribution) paired with the
        // higher actual total -- monotonically aligned, so with exactly 2
        // points the correlation must come out to exactly +1.0.
        Player wr = playerRepository.save(new Player("Backtest WR", "WR", ari, 21, "ACTIVE", LocalDate.of(1997, 1, 1)));
        saveActualStats(wr, targetGame, ari, new BigDecimal("8.0"));

        Player rb = playerRepository.save(new Player("Backtest RB", "RB", ari, 22, "ACTIVE", LocalDate.of(1997, 1, 1)));
        saveActualStats(rb, targetGame, ari, new BigDecimal("20.0"));

        // A third player whose *current* injury status is OUT -- their
        // recommendation is dominated by the -1000 override, but they also
        // have real box-score points on file (simulating "current status
        // says OUT, but they played in this past game"), which must be
        // excluded from the correlation rather than blowing it up.
        Player injured = playerRepository.save(new Player("Backtest Injured WR", "WR", ari, 23, "ACTIVE", LocalDate.of(1997, 1, 1)));
        injuryReconciliationService.resolveOrCreateFromEspn(injured, "OUT", LocalDate.of(2097, 9, 5));
        saveActualStats(injured, targetGame, ari, new BigDecimal("12.0"));

        BacktestResult result = backtestService.runBacktest(SEASON);

        assertThat(result.season()).isEqualTo(SEASON);
        assertThat(result.weeksEvaluated()).isEqualTo(1); // only TARGET_WEEK has games
        assertThat(result.recommendationsEvaluated()).isEqualTo(3);
        assertThat(result.excludedDueToInjuryOverride()).isEqualTo(1);
        assertThat(result.matchedWithActualStats()).isEqualTo(2);
        assertThat(result.overallCorrelation()).isNotNull();
        assertThat(result.overallCorrelation()).isCloseTo(1.0, offset(0.0001));
        assertThat(result.correlationByPosition()).containsKeys("WR", "RB");
    }

    private Team team(String abbreviation) {
        return teamRepository.findByAbbreviation(abbreviation).orElseThrow();
    }

    private void saveActualStats(Player player, Game game, Team team, BigDecimal fantasyPointsPpr) {
        playerGameStatsRepository.save(new PlayerGameStats(player, game, team,
                5, 4, 40, 0, 0, null, null, null, null, null, 0, fantasyPointsPpr, fantasyPointsPpr));
    }
}
