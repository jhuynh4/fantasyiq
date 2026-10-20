package com.fantasyiq.api.controller;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WeekControllerIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private TeamRepository teamRepository;

    @Test
    void requiresALogin() throws Exception {
        mockMvc.perform(get("/api/weeks/current")).andExpect(status().isUnauthorized());
    }

    @Test
    void reportsTheWeekOfTheNextGameToAnyLoggedInUser() throws Exception {
        Team home = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Team away = teamRepository.findByAbbreviation("ATL").orElseThrow();
        gameRepository.save(new Game("week-it-upcoming", 2097, 8, home, away,
                Instant.now().plus(Duration.ofDays(1)), null, "SCHEDULED"));

        mockMvc.perform(get("/api/weeks/current").header("Authorization", "Bearer " + registerAndGetAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(2097))
                .andExpect(jsonPath("$.week").value(8));
    }

    @Test
    void isNoContentWhenThereAreNoGamesInRange() throws Exception {
        mockMvc.perform(get("/api/weeks/current").header("Authorization", "Bearer " + registerAndGetAccessToken()))
                .andExpect(status().isNoContent());
    }

    private String registerAndGetAccessToken() throws Exception {
        String email = "week+%d@fantasyiq.dev".formatted(System.nanoTime());
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(email, "correct-horse-battery", "Coach"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private record RegisterPayload(String email, String password, String displayName) {
    }
}
