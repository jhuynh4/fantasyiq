package com.fantasyiq.api.controller;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TradeControllerIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private PlayerGameStatsRepository playerGameStatsRepository;

    @Test
    void analyzeReturnsValueComparisonForARealTrade() throws Exception {
        Player playerA = wrWithRecentPoints("Trade Controller WR A", new BigDecimal("20.00"));
        Player playerB = wrWithRecentPoints("Trade Controller WR B", new BigDecimal("10.00"));

        mockMvc.perform(post("/api/trades/analyze")
                        .with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TradePayload(List.of(playerA.getId()), List.of(playerB.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sideA.players[0].playerId").value(playerA.getId().toString()))
                .andExpect(jsonPath("$.sideB.players[0].playerId").value(playerB.getId().toString()))
                .andExpect(jsonPath("$.valueDelta").isNumber());
    }

    @Test
    void emptySideReturnsBadRequest() throws Exception {
        Player playerA = wrWithRecentPoints("Empty Side WR", new BigDecimal("10.00"));

        mockMvc.perform(post("/api/trades/analyze")
                        .with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TradePayload(List.of(playerA.getId()), List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPlayerIdReturnsNotFound() throws Exception {
        Player playerA = wrWithRecentPoints("Known WR", new BigDecimal("10.00"));

        mockMvc.perform(post("/api/trades/analyze")
                        .with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TradePayload(List.of(playerA.getId()), List.of(UUID.randomUUID())))))
                .andExpect(status().isNotFound());
    }

    private int jerseyCounter = 1;

    private Player wrWithRecentPoints(String name, BigDecimal fantasyPointsPpr) {
        Team ari = teamRepository.findByAbbreviation("ARI").orElseThrow();
        Team sf = teamRepository.findByAbbreviation("SF").orElseThrow();
        Player player = playerRepository.save(new Player(name, "WR", ari, jerseyCounter++, "ACTIVE", LocalDate.of(1998, 1, 1)));
        // external_ref is VARCHAR(50) -- a longer prefix here overflowed it
        // (caught in CI, not locally): "trade-controller-it-" + a full UUID
        // is 57 chars.
        Game game = gameRepository.save(new Game(
                "tc-it-" + UUID.randomUUID(), 2093, 1, ari, sf, Instant.parse("2093-09-07T17:00:00Z"),
                "Test Stadium", "FINAL"));
        playerGameStatsRepository.save(new PlayerGameStats(player, game, ari, 5, 4, 40, 0, 0, null, null, null, null,
                null, 0, fantasyPointsPpr, fantasyPointsPpr));
        return player;
    }

    private record TradePayload(List<UUID> sideAPlayerIds, List<UUID> sideBPlayerIds) {
    }
}
