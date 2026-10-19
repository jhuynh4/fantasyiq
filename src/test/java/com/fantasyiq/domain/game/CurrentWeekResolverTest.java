package com.fantasyiq.domain.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentWeekResolverTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @Mock
    private GameRepository gameRepository;
    @InjectMocks
    private CurrentWeekResolver resolver;

    @Test
    void returnsTheSeasonAndWeekOfTheNextGame() {
        when(gameRepository.findFirstByKickoffGreaterThanEqualOrderByKickoffAsc(any()))
                .thenReturn(Optional.of(gameKickingOffAt(NOW.plus(Duration.ofDays(1)), 2026, 7)));

        assertThat(resolver.resolve(NOW)).contains(new CurrentWeekResolver.SeasonWeek(2026, 7));
    }

    @Test
    void looksBackFarEnoughThatAGameStillBeingPlayedKeepsItsWeekCurrent() {
        when(gameRepository.findFirstByKickoffGreaterThanEqualOrderByKickoffAsc(any())).thenReturn(Optional.empty());

        resolver.resolve(NOW);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(gameRepository).findFirstByKickoffGreaterThanEqualOrderByKickoffAsc(from.capture());
        assertThat(from.getValue()).isEqualTo(NOW.minus(CurrentWeekResolver.LOOKBACK));
    }

    @Test
    void returnsNothingWhenThereIsNoLaterGameAtAll() {
        when(gameRepository.findFirstByKickoffGreaterThanEqualOrderByKickoffAsc(any())).thenReturn(Optional.empty());

        assertThat(resolver.resolve(NOW)).isEmpty();
    }

    @Test
    void returnsNothingWhenTheNextGameIsBeyondTheHorizon() {
        Instant justPastTheHorizon = NOW.plus(CurrentWeekResolver.HORIZON).plusSeconds(1);
        when(gameRepository.findFirstByKickoffGreaterThanEqualOrderByKickoffAsc(any()))
                .thenReturn(Optional.of(gameKickingOffAt(justPastTheHorizon, 2026, 1)));

        assertThat(resolver.resolve(NOW)).isEmpty();
    }

    @Test
    void aGameExactlyAtTheHorizonStillCounts() {
        when(gameRepository.findFirstByKickoffGreaterThanEqualOrderByKickoffAsc(any()))
                .thenReturn(Optional.of(gameKickingOffAt(NOW.plus(CurrentWeekResolver.HORIZON), 2026, 1)));

        assertThat(resolver.resolve(NOW)).contains(new CurrentWeekResolver.SeasonWeek(2026, 1));
    }

    private static Game gameKickingOffAt(Instant kickoff, int season, int week) {
        return new Game("ref-" + kickoff, season, week, null, null, kickoff, null, "SCHEDULED");
    }
}
