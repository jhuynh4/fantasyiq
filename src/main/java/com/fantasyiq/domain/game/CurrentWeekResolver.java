package com.fantasyiq.domain.game;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Works out which season/week is "current" from the schedule itself rather
 * than a hardcoded calendar: the week of the earliest game that hasn't
 * kicked off yet, or kicked off recently enough to still be playing.
 *
 * Kickoff-based, deliberately not status-based -- a game stuck in a non-final
 * status (postponed, or ESPN simply not updated yet) would otherwise pin the
 * "current" week forever. The lookback is a game's length plus margin, so a
 * Monday-night game's week stays current until it's over and the next week
 * takes over from roughly that point, not from the following midday.
 *
 * Nothing is returned when the next game is further out than the horizon (the
 * offseason, once the new schedule is published) or when there is no later
 * game at all (after the final week) -- callers should do nothing then rather
 * than score a week nobody is about to play.
 */
@Service
public class CurrentWeekResolver {

    static final Duration LOOKBACK = Duration.ofHours(6);
    static final Duration HORIZON = Duration.ofDays(10);

    private final GameRepository gameRepository;

    public CurrentWeekResolver(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public Optional<SeasonWeek> resolve(Instant now) {
        return gameRepository.findFirstByKickoffGreaterThanEqualOrderByKickoffAsc(now.minus(LOOKBACK))
                .filter(game -> !game.getKickoff().isAfter(now.plus(HORIZON)))
                .map(game -> new SeasonWeek(game.getSeason(), game.getWeek()));
    }

    public record SeasonWeek(int season, int week) {
    }
}
