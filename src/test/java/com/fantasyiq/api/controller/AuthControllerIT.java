package com.fantasyiq.api.controller;

import com.fantasyiq.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the register -> login -> refresh flow through real Spring Security
 * filters and a real Postgres instance (via IntegrationTestBase), not mocks.
 */
@AutoConfigureMockMvc
class AuthControllerIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerThenLoginThenRefreshReturnsFreshTokens() throws Exception {
        String email = "newcoach+%d@fantasyiq.dev".formatted(System.nanoTime());

        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterPayload(email, "correct-horse-battery", "Coach"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(registerResponse).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));
    }

    @Test
    void refreshTokenCannotBeReplayedAfterRotation() throws Exception {
        String email = "rotator+%d@fantasyiq.dev".formatted(System.nanoTime());

        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterPayload(email, "correct-horse-battery", "Coach"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(registerResponse).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String email = "dupe+%d@fantasyiq.dev".formatted(System.nanoTime());
        String body = objectMapper.writeValueAsString(new RegisterPayload(email, "correct-horse-battery", "Coach"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        String email = "wrongpass+%d@fantasyiq.dev".formatted(System.nanoTime());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterPayload(email, "correct-horse-battery", "Coach"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    private record RegisterPayload(String email, String password, String displayName) {
    }

    private record LoginPayload(String email, String password) {
    }

    private record RefreshPayload(String refreshToken) {
    }
}
