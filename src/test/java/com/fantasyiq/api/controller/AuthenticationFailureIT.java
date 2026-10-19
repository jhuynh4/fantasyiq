package com.fantasyiq.api.controller;

import com.fantasyiq.IntegrationTestBase;
import com.fantasyiq.auth.JwtProperties;
import com.fantasyiq.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A client deciding whether to refresh its access token needs missing,
 * malformed and expired tokens to come back as 401 -- Spring Security's
 * default for an unauthenticated request with no custom entry point is 403,
 * which a client can't tell apart from a genuine permissions failure.
 */
@AutoConfigureMockMvc
class AuthenticationFailureIT extends IntegrationTestBase {

    private static final String PROTECTED_PATH = "/api/players/search?q=a";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void requestWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void requestWithAMalformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithAnExpiredTokenIsUnauthorized() throws Exception {
        JwtService expiringImmediately = new JwtService(
                new JwtProperties(jwtProperties.secret(), -1, jwtProperties.refreshTokenTtlDays()));
        String expiredToken = expiringImmediately.generateAccessToken(UUID.randomUUID(), "expired@fantasyiq.dev");

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithAValidTokenIsStillAllowed() throws Exception {
        String accessToken = registerAndGetAccessToken();

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void authResponsesIncludeTheAccessTokenLifetimeInSeconds() throws Exception {
        String email = "expires+%d@fantasyiq.dev".formatted(System.nanoTime());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(email, "correct-horse-battery", "Coach"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresIn").value((int) (jwtProperties.accessTokenTtlMinutes() * 60)));
    }

    private String registerAndGetAccessToken() throws Exception {
        String email = "valid+%d@fantasyiq.dev".formatted(System.nanoTime());
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
