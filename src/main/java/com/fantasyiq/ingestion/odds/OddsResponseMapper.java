package com.fantasyiq.ingestion.odds;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Picks the first bookmaker in each game's list rather than averaging
 * across several -- consistency matters more than optimality here (see
 * docs/data-source-integration.md section 2.2). Games missing a spreads or
 * totals market from that bookmaker are skipped, not failed -- a
 * market being temporarily absent is a legitimate gap, not a data error.
 */
final class OddsResponseMapper {

    private OddsResponseMapper() {
    }

    static List<RawGameOdds> toRawGameOdds(OddsGame[] games) {
        if (games == null) {
            return List.of();
        }

        List<RawGameOdds> result = new ArrayList<>();
        for (OddsGame game : games) {
            toRawGameOdds(game).ifPresent(result::add);
        }
        return result;
    }

    private static Optional<RawGameOdds> toRawGameOdds(OddsGame game) {
        if (game == null || game.bookmakers() == null || game.bookmakers().isEmpty()) {
            return Optional.empty();
        }

        OddsBookmaker bookmaker = game.bookmakers().get(0);
        Optional<OddsMarket> spreads = findMarket(bookmaker, "spreads");
        Optional<OddsMarket> totals = findMarket(bookmaker, "totals");

        if (spreads.isEmpty() || totals.isEmpty()) {
            return Optional.empty();
        }

        Optional<BigDecimal> homeSpread = findOutcomePoint(spreads.get(), game.homeTeam());
        Optional<BigDecimal> awaySpread = findOutcomePoint(spreads.get(), game.awayTeam());
        Optional<BigDecimal> overUnder = totals.get().outcomes() == null ? Optional.empty()
                : totals.get().outcomes().stream()
                        .map(OddsOutcome::point)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .map(BigDecimal::valueOf);

        if (homeSpread.isEmpty() || awaySpread.isEmpty() || overUnder.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RawGameOdds(game.homeTeam(), game.awayTeam(), parseCommenceTime(game.commenceTime()),
                bookmaker.key(), homeSpread.get(), awaySpread.get(), overUnder.get()));
    }

    private static Optional<OddsMarket> findMarket(OddsBookmaker bookmaker, String key) {
        if (bookmaker.markets() == null) {
            return Optional.empty();
        }
        return bookmaker.markets().stream().filter(m -> key.equals(m.key())).findFirst();
    }

    private static Optional<BigDecimal> findOutcomePoint(OddsMarket market, String teamName) {
        if (market.outcomes() == null) {
            return Optional.empty();
        }
        return market.outcomes().stream()
                .filter(o -> teamName != null && teamName.equals(o.name()))
                .map(OddsOutcome::point)
                .filter(Objects::nonNull)
                .findFirst()
                .map(BigDecimal::valueOf);
    }

    private static Instant parseCommenceTime(String commenceTime) {
        return commenceTime == null ? null : Instant.parse(commenceTime);
    }
}
