package com.fantasyiq.cache;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.recommendation.RecommendationSnapshot;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real DB fixtures, same reasoning as StartSitRecommendationServiceIT -- no
 * vendor to stub, this is pure composition over already-existing domain
 * data (here, over Redis + Postgres rather than just Postgres).
 */
class RecommendationCacheServiceIT extends IntegrationTestBase {

    private static final int SEASON = 2097;
    private static final int WEEK = 3;

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private RecommendationRepository recommendationRepository;
    @Autowired
    private RecommendationCacheService recommendationCacheService;

    @Test
    void getStartSitServesFromCacheOnceLoaded() {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Player player = playerRepository.save(new Player("Cache Test WR", "WR", ari, 88, "ACTIVE", LocalDate.of(1998, 1, 1)));
        recommendationRepository.save(new Recommendation(player, SEASON, WEEK, "START_SIT", BigDecimal.TEN, "HIGH", "v1"));

        List<RecommendationSnapshot> firstRead = recommendationCacheService.getStartSit(SEASON, WEEK);
        assertThat(firstRead).hasSize(1);

        // Deleting straight from the DB, bypassing refreshStartSit entirely --
        // if the second read still finds a row, it can only be the cache.
        recommendationRepository.deleteAll();
        List<RecommendationSnapshot> secondRead = recommendationCacheService.getStartSit(SEASON, WEEK);
        assertThat(secondRead).hasSize(1);
    }

    @Test
    void refreshStartSitRepopulatesTheCacheFromTheCurrentDbState() {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Player player = playerRepository.save(new Player("Refresh Test WR", "WR", ari, 89, "ACTIVE", LocalDate.of(1998, 1, 1)));
        recommendationRepository.save(new Recommendation(player, SEASON, WEEK, "START_SIT", BigDecimal.TEN, "HIGH", "v1"));
        recommendationCacheService.getStartSit(SEASON, WEEK);

        recommendationRepository.deleteAll();
        List<RecommendationSnapshot> refreshed = recommendationCacheService.refreshStartSit(SEASON, WEEK);

        assertThat(refreshed).isEmpty();
        assertThat(recommendationCacheService.getStartSit(SEASON, WEEK)).isEmpty();
    }
}
