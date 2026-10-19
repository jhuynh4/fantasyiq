package com.fantasyiq.api.controller;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.auth.User;
import com.fantasyiq.auth.UserRepository;
import com.fantasyiq.auth.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration is open, so any user can obtain a valid token -- these prove
 * that a token alone no longer lets a caller start ingestion, scoring or a
 * backtest. The forbidden cases never reach the controller, so none of them
 * actually triggers a job.
 */
@AutoConfigureMockMvc
class AdminAuthorizationIT extends IntegrationTestBase {

    private static final String PASSWORD = "correct-horse-battery";

    private static final List<String> ADMIN_ONLY_POSTS = List.of(
            "/api/players/ingest",
            "/api/games/ingest",
            "/api/injuries/ingest",
            "/api/player-game-stats/ingest?season=2099",
            "/api/weather/ingest?season=2099&week=1",
            "/api/odds/ingest",
            "/api/defense-vs-position/compute?season=2099&week=1",
            "/api/recommendations/generate?season=2099&week=1",
            "/api/recommendations/backtest?season=2099");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void anonymousCallersAreUnauthorizedOnEveryAdminEndpoint() throws Exception {
        for (String path : ADMIN_ONLY_POSTS) {
            mockMvc.perform(post(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void aRegularUserIsForbiddenFromEveryAdminEndpoint() throws Exception {
        String token = registerRegularUser();

        for (String path : ADMIN_ONLY_POSTS) {
            mockMvc.perform(post(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    @Test
    void anAdminIsAllowedToTriggerScoring() throws Exception {
        String token = loginAsNewAdmin();

        mockMvc.perform(post("/api/recommendations/generate?season=2099&week=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void aRegularUserCanStillUseTheReadEndpoints() throws Exception {
        String token = registerRegularUser();

        mockMvc.perform(get("/api/recommendations/start-sit?season=2099&week=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/players/search?q=a").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void registrationNeverProducesAnAdmin() throws Exception {
        String email = "selfpromote+%d@fantasyiq.dev".formatted(System.nanoTime());
        register(email);

        assertThat(userRepository.findByEmail(email).orElseThrow().getRole()).isEqualTo(UserRole.USER);
    }

    private String registerRegularUser() throws Exception {
        return register("regular+%d@fantasyiq.dev".formatted(System.nanoTime()));
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CredentialsPayload(email, PASSWORD, "Coach"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String loginAsNewAdmin() throws Exception {
        String email = "admin+%d@fantasyiq.dev".formatted(System.nanoTime());
        userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), "Admin", UserRole.ADMIN));

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private record CredentialsPayload(String email, String password, String displayName) {
    }

    private record LoginPayload(String email, String password) {
    }
}
