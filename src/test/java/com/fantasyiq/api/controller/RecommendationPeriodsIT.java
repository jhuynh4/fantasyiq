package com.fantasyiq.api.controller;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RecommendationPeriodsIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private RecommendationRepository recommendationRepository;

    @Test
    void requiresALogin() throws Exception {
        mockMvc.perform(get("/api/recommendations/periods")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyTheWeeksThatHaveScoresNewestSeasonFirst() throws Exception {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Player first = playerRepository.save(new Player("Periods WR A", "WR", ari, 1, "ACTIVE", LocalDate.of(1998, 1, 1)));
        Player second = playerRepository.save(new Player("Periods WR B", "WR", ari, 2, "ACTIVE", LocalDate.of(1998, 1, 1)));

        // Two players in the same week must not repeat that week; weeks come back ascending.
        save(first, 2099, 3, "START_SIT");
        save(second, 2099, 3, "START_SIT");
        save(first, 2099, 1, "START_SIT");
        save(first, 2098, 5, "START_SIT");
        // Another recommendation type does not make a week available for start/sit.
        save(first, 2098, 9, "WAIVER");

        mockMvc.perform(get("/api/recommendations/periods").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].season").value(2099))
                .andExpect(jsonPath("$[0].weeks.length()").value(2))
                .andExpect(jsonPath("$[0].weeks[0]").value(1))
                .andExpect(jsonPath("$[0].weeks[1]").value(3))
                .andExpect(jsonPath("$[1].season").value(2098))
                .andExpect(jsonPath("$[1].weeks.length()").value(1))
                .andExpect(jsonPath("$[1].weeks[0]").value(5));
    }

    @Test
    void isAnEmptyListBeforeAnythingHasBeenScored() throws Exception {
        mockMvc.perform(get("/api/recommendations/periods").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void save(Player player, int season, int week, String type) {
        recommendationRepository.save(new Recommendation(player, season, week, type, new BigDecimal("10.000"), "HIGH", "v2"));
    }
}
