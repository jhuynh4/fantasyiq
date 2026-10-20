package com.fantasyiq.api.controller;

import com.fantasyiq.domain.game.CurrentWeekResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.emptyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller behavior only, with no Spring context or database -- the security
 * rules and the real query are covered by WeekControllerIT.
 */
@ExtendWith(MockitoExtension.class)
class WeekControllerTest {

    @Mock
    private CurrentWeekResolver currentWeekResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WeekController(currentWeekResolver)).build();
    }

    @Test
    void returnsTheSeasonAndWeekWhenThereIsACurrentWeek() throws Exception {
        when(currentWeekResolver.resolve(any())).thenReturn(Optional.of(new CurrentWeekResolver.SeasonWeek(2026, 7)));

        mockMvc.perform(get("/api/weeks/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(2026))
                .andExpect(jsonPath("$.week").value(7));
    }

    @Test
    void returnsNoContentRatherThanAnErrorWhenThereIsNoCurrentWeek() throws Exception {
        when(currentWeekResolver.resolve(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/weeks/current"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(emptyString()));
    }
}
