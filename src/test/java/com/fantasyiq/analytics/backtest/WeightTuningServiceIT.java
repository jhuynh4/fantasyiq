package com.fantasyiq.analytics.backtest;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.stats.DefenseVsPositionStats;
import com.fantasyiq.domain.stats.DefenseVsPositionStatsRepository;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import com.fantasyiq.analytics.startsit.StartSitRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Three fixture WR players, each facing a different opponent with a
 * different prior-week matchup rank (so each gets a distinct MATCHUP
 * contribution), with actual fantasy points deliberately set to
 * 1.5 * contribution + 2.0 -- an exact linear relationship, so the fitted
 * regression should recover a slope close to 1.5 and a strong positive
 * correlation, proving the analysis pipeline end-to-end rather than just
 * that the math works in isolation (already covered by
 * SimpleLinearRegressionTest).
 */
class WeightTuningServiceIT extends IntegrationTestBase {

    private static final int SEASON = 2096;
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
    private RecommendationRepository recommendationRepository;
    @Autowired
    private StartSitRecommendationService startSitRecommendationService;
    @Autowired
    private WeightTuningService weightTuningService;

    @BeforeEach
    void cleanUp() {
        recommendationRepository.deleteAll();
        defenseVsPositionStatsRepository.deleteAll();
        playerGameStatsRepository.deleteAll();
        gameRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void suggestsARegressionSlopeForMatchupAndReportsNoDataForUnusedFactors() {
        Team ari = team("ARI");
        Team dal = team("DAL");
        Team phi = team("PHI");
        Team sf = team("SF");
        Team nyg = team("NYG");
        Team det = team("DET");

        // Distinct prior-week matchup ranks vs WR for each opponent ->
        // distinct MATCHUP contributions for each fixture player.
        defenseVsPositionStatsRepository.save(new DefenseVsPositionStats(sf, SEASON, PRIOR_WEEK, "WR",
                BigDecimal.valueOf(30), BigDecimal.valueOf(30), 5, 5));
        defenseVsPositionStatsRepository.save(new DefenseVsPositionStats(nyg, SEASON, PRIOR_WEEK, "WR",
                BigDecimal.valueOf(20), BigDecimal.valueOf(20), 15, 15));
        defenseVsPositionStatsRepository.save(new DefenseVsPositionStats(det, SEASON, PRIOR_WEEK, "WR",
                BigDecimal.valueOf(10), BigDecimal.valueOf(10), 28, 28));

        savePlayerFacingOpponent("WR vs SF", ari, sf, new BigDecimal("6.85"));
        savePlayerFacingOpponent("WR vs NYG", dal, nyg, new BigDecimal("18.94"));
        savePlayerFacingOpponent("WR vs DET", phi, det, new BigDecimal("34.66"));

        int generated = startSitRecommendationService.computeForWeek(SEASON, TARGET_WEEK);
        assertThat(generated).isEqualTo(3);

        WeightTuningResult result = weightTuningService.analyzeWeights(SEASON);

        assertThat(result.season()).isEqualTo(SEASON);
        assertThat(result.totalMatchedRecommendations()).isEqualTo(3);

        FactorTuningSuggestion matchup = suggestionFor(result, "MATCHUP");
        assertThat(matchup.dataPoints()).isEqualTo(3);
        assertThat(matchup.correlationWithActual()).isGreaterThan(0.999);
        assertThat(matchup.suggestedScaleMultiplier()).isCloseTo(1.5, offset(0.05));

        // No betting_lines/weather_forecasts/usage-history fixtures set up
        // for these players -- those factors never fired, so there's
        // nothing to regress against ("we can't tell", not "no effect").
        FactorTuningSuggestion vegas = suggestionFor(result, "VEGAS");
        assertThat(vegas.dataPoints()).isZero();
        assertThat(vegas.correlationWithActual()).isNull();
        assertThat(vegas.suggestedScaleMultiplier()).isNull();
    }

    private FactorTuningSuggestion suggestionFor(WeightTuningResult result, String factorType) {
        Optional<FactorTuningSuggestion> match = result.suggestions().stream()
                .filter(s -> s.factorType().equals(factorType))
                .findFirst();
        assertThat(match).isPresent();
        return match.get();
    }

    private Team team(String abbreviation) {
        return teamRepository.findByAbbreviation(abbreviation).orElseThrow();
    }

    private void savePlayerFacingOpponent(String playerName, Team offenseTeam, Team opponent, BigDecimal actualPoints) {
        Player player = playerRepository.save(new Player(playerName, "WR", offenseTeam, 80, "ACTIVE", LocalDate.of(1997, 1, 1)));
        Game game = gameRepository.save(new Game("tune-" + playerName, SEASON, TARGET_WEEK, offenseTeam, opponent,
                Instant.parse("2096-09-07T17:00:00Z"), "Test Stadium", "FINAL"));
        playerGameStatsRepository.save(new PlayerGameStats(player, game, offenseTeam,
                5, 4, 40, 0, 0, null, null, null, null, null, 0, actualPoints, actualPoints));
    }
}
