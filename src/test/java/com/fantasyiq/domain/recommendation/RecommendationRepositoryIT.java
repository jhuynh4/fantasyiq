package com.fantasyiq.domain.recommendation;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recommendation.player and .factors are EAGER, but Hibernate loads eager
 * associations of a query-method result with one follow-up query per row, so
 * reading a week of N recommendations used to cost 1 + 2N statements (about
 * 1,600 for a normal week). These count the statements an actual read issues,
 * so growth with the number of rows fails here instead of showing up as a slow
 * cache miss or backtest.
 */
class RecommendationRepositoryIT extends IntegrationTestBase {

    private static final int SEASON = 2096;
    private static final int WEEK = 3;
    private static final int RECOMMENDATIONS = 10;
    private static final int FACTORS_EACH = 3;

    @Autowired
    private RecommendationRepository recommendationRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void readingAWeekIsASingleQueryNoMatterHowManyRowsThereAre() {
        saveRecommendations();

        List<Recommendation> result = countingStatements("week read", () ->
                recommendationRepository.findBySeasonAndWeekAndType(SEASON, WEEK, "START_SIT"));

        assertEveryAssociationIsLoaded(result);
    }

    @Test
    void readingASeasonIsASingleQueryNoMatterHowManyRowsThereAre() {
        saveRecommendations();

        List<Recommendation> result = countingStatements("season read", () ->
                recommendationRepository.findBySeasonAndType(SEASON, "START_SIT"));

        assertEveryAssociationIsLoaded(result);
    }

    private <T> T countingStatements(String what, Supplier<T> read) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            T result = read.get();
            assertThat(statistics.getPrepareStatementCount())
                    .as("statements issued by the %s for %d recommendations", what, RECOMMENDATIONS)
                    .isEqualTo(1);
            return result;
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private void assertEveryAssociationIsLoaded(List<Recommendation> result) {
        assertThat(result).hasSize(RECOMMENDATIONS);
        // Read after the query's session has closed, so an association that was only
        // lazily available would throw here instead of passing.
        assertThat(result).allSatisfy(recommendation -> {
            assertThat(recommendation.getFactors()).hasSize(FACTORS_EACH);
            assertThat(recommendation.getPlayer().getCurrentTeam().getAbbreviation()).isEqualTo("ARI");
        });
    }

    private void saveRecommendations() {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        for (int i = 0; i < RECOMMENDATIONS; i++) {
            Player player = playerRepository.save(
                    new Player("Query Count Player " + i, "WR", ari, i, "ACTIVE", LocalDate.of(1998, 1, 1)));
            Recommendation recommendation = new Recommendation(
                    player, SEASON, WEEK, "START_SIT", BigDecimal.valueOf(i), "MEDIUM", "v1");
            recommendation.replaceFactors(List.of(
                    factor(recommendation, "MATCHUP"),
                    factor(recommendation, "VEGAS"),
                    factor(recommendation, "USAGE")));
            recommendationRepository.save(recommendation);
        }
    }

    private static RecommendationFactor factor(Recommendation recommendation, String type) {
        return new RecommendationFactor(recommendation, type, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                "narrative for " + type);
    }
}
