package com.fantasyiq.api.controller;

import com.fantasyiq.api.dto.CurrentWeekResponse;
import com.fantasyiq.domain.game.CurrentWeekResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/weeks")
public class WeekController {

    private final CurrentWeekResolver currentWeekResolver;

    public WeekController(CurrentWeekResolver currentWeekResolver) {
        this.currentWeekResolver = currentWeekResolver;
    }

    /**
     * The week a client should show by default -- the same one the daily scoring job
     * uses. 204, not an error, when there isn't one (the offseason, or after the
     * final week): "no current week" is a normal state a client picks a week itself for.
     */
    @GetMapping("/current")
    public ResponseEntity<CurrentWeekResponse> current() {
        return currentWeekResolver.resolve(Instant.now())
                .map(week -> ResponseEntity.ok(new CurrentWeekResponse(week.season(), week.week())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
