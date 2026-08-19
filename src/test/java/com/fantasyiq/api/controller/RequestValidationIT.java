package com.fantasyiq.api.controller;

import com.fantasyiq.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves @RequestParam constraint violations (HandlerMethodValidationException)
 * return a clean 400 instead of falling through to GlobalExceptionHandler's
 * catch-all 500 -- a real gap until this Phase 4 slice, since only
 * MethodArgumentNotValidException (@Valid @RequestBody) was previously
 * handled. Covers two different controllers to prove the fix is general,
 * not a one-off on a single endpoint.
 */
@AutoConfigureMockMvc
class RequestValidationIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void outOfRangeWeekReturnsBadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/api/recommendations/start-sit?season=2025&week=99").with(user("tester")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void zeroSeasonReturnsBadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/api/recommendations/tune-weights?season=0").with(user("tester")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void blankSearchQueryReturnsBadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/api/players/search?q=").with(user("tester")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
