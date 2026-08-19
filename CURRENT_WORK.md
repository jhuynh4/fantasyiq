# Current Work

## Status: Phase 5 first slice (trade analyzer) merged — no active branch

`phase-5/trade-analyzer` (rest-of-season trade value comparison) is merged to `main` (PR #16) and the local branch is cleaned up. Everything described below is on `main`.

## What's been completed (Phases 1-4, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2** — games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle, `defense_vs_position_stats`, `weather_forecasts`, `betting_lines`.

**Phase 3** — start/sit recommendation engine (6 factors), player trending endpoint, backtest validation, weight tuning + recent-performance factor (took real predictive correlation from `0.0137` to `0.306`).

**Phase 4 — fully complete**: Redis caching (`GET /recommendations/start-sit` 177ms → 4.6ms avg, `GET /players/{id}` 25.5ms → 3.6ms avg), Bucket4j rate limiting on `/api/auth/**`, Bean Validation on previously-unconstrained request params. Full writeups in `CLAUDE.md`.

## Phase 5, first slice: Trade analyzer (PR #16, merged)

- **`POST /api/trades/analyze`** (`analytics.trade.TradeAnalysisService`) — rest-of-season value comparison between two arbitrary-length player lists.
- Reuses only the three factor calculators that describe a player's *current state*, not a specific future game: `RecentPerformanceFactorCalculator`, `UsageTrendFactorCalculator`, `InjuryFactorCalculator`. Matchup/Vegas/Weather deliberately excluded — no known future opponent, no forecast beyond ~5 days, no odds posted that far out; reusing them against a guessed future week would fabricate a signal.
- **Positional scarcity**: trade value is `score - replacementLevel[position]`, not a raw score — replacement level is the score at a realistic roster-startable cutoff rank per position (QB12/RB24/WR24/TE12), so an RB and a WR are comparable on one scale.
- A named player always appears in the response, even with zero data (`null` score/value, not silently dropped or fabricated) — different from start/sit's "no data, no row" rule, since a trade request explicitly names specific players.
- Computed fresh per request, not cached/persisted — no natural "week" to key a cache on.
- `RecommendationFactorResponse` renamed to `FactorResponse` since trade responses need the identical `(factorType, contribution, narrative)` shape and the old name would have been misleading once shared.
- **Verified live** against real 2025 data (1-for-1 and 2-for-1 trades, empty-side 400, unknown-player-id 404). **Measured real cost: ~3.3s per request** — replacement-level computation scores every player at every position on every call, a known N+1-shaped tradeoff accepted for this first slice (same reasoning as `BacktestService`'s similar pattern). Fix if it becomes a real problem: batch the per-player queries, not add caching.
- `TradeAnalysisServiceIT` proves real factor-calculator composition (not reimplementation): one test hand-computes exact expected scores from `RecentPerformanceFactorCalculator`'s own `WEIGHT` constant, another confirms an `OUT` player gets the identical `-1000` penalty the start/sit engine applies.
- One CI-only bug: a test fixture's `external_ref` string (57 chars) overflowed the `VARCHAR(50)` column — fixed by shortening the test's id prefix.

Full design rationale in `CLAUDE.md`'s "Trade analyzer" section.

## What remains (lower priority, not blocking)

- Trade analyzer performance (N+1 query pattern in `computeReplacementLevels`, ~3.3s per request) — not urgent, occasional endpoint, same acceptance already established for the backtest job
- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one)
- Backtest performance (N+1 query pattern inside `gatherFactors`, ~18 min for a full season) — not urgent, occasional endpoint
- `MatchupFactorCalculator`'s long-run uniform averaging and the weak `USAGE` factor remain real, un-investigated hypotheses if further model improvement is wanted later

## Recommended next steps

Phase 5's dev-plan checklist (rest-of-season value model, positional scarcity, `POST /api/trades/analyze`, edge case handling) is closed. Two real directions from here, neither started yet:

1. **Batch the trade analyzer's replacement-level computation** if the 3.3s cost turns out to matter in practice.
2. **Move to Phase 6** — observability & production readiness (Actuator metrics, centralized log shipping, alerting), per `docs/development-plan.md`.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, `phase-3/start-sit-scoring-engine`, `phase-3/player-trending-endpoint`, and `phase-3/backtest-validation` still exist on origin from prior slices (deferred cleanup, unchanged). `phase-5/trade-analyzer` has been deleted both locally and remotely.

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas").
