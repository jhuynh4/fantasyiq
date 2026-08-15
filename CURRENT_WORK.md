# Current Work

## Status: backtest endpoint built and verified against real data — ready to push for CI

Branch `phase-3/backtest-validation` adds `POST /api/recommendations/backtest?season=`, closing out the last open item from Phase 3's original checklist. Built clean via IntelliJ, manually verified end-to-end against the full real 2025 season, and a real performance bug was found and fixed along the way (see below).

## What's been completed (Phase 1 + Phase 2, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2 — fully complete**: games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle (audit logging, Resilience4j, scheduling, structured logging), `defense_vs_position_stats` (computed), `weather_forecasts` (OpenWeatherMap), `betting_lines` (The Odds API).

## Phase 3 (merged)

**Start/sit recommendation engine** — scope deliberately narrowed to start/sit only at first; waiver, trade, and rankings all reuse the same factor engine per `docs/development-plan.md`.

- `V13`/`V14` migrations: `recommendations` + `recommendation_factors` tables
- New `domain.recommendation` package (entities + reconciliation), new `analytics.scoring` package (`FactorResult` + five pure calculators: Matchup, VegasImpliedTotal, Weather, Injury, UsageTrend), new `analytics.startsit.StartSitRecommendationService`
- Endpoints: `POST /api/recommendations/generate?season=&week=`, `GET /api/recommendations/start-sit?season=&week=&position=`
- `RedZoneFactorCalculator`/`StrengthOfScheduleFactorCalculator` deliberately dropped from the original plan (ESPN never provides red zone touches; SOS is a better fit for waiver/trade) — docs updated to say so.
- Manually verified against real 2025 data: 760 real recommendations generated for week 8, math/confidence/injury-override all spot-checked correct.

**Player trending endpoint** — the other checklist item worth building (`GET /api/players/{id}/trending`), reusing `UsageTrendFactorCalculator` directly against a player's 4 most recent games with no season/week boundary. `GET /api/rankings` was deliberately skipped as a near-duplicate of the start-sit endpoint.

See `CLAUDE.md`'s "Start/sit recommendation engine" and "API endpoints" sections for the full design writeup.

**Real bugs found and fixed during this phase** (beyond the features themselves) — all now documented in `CLAUDE.md`:
1. An `ingestion_runs.source` audit constant exceeded its `VARCHAR(30)` column.
2. `recommendation_factors.factor_weight` was sized for a fractional 0..1 weight; widened via `V14` to match the actual point-scale weights used.
3. Both of the above were surfacing as a misleading `403` instead of a real status — `GlobalExceptionHandler` now has a catch-all handler returning a real `500` and logging server-side.
4. **A significant, unrelated pre-existing IT test infrastructure bug**: `IntegrationTestBase`'s shared Testcontainers Postgres was tied to per-class `@Container`/`@Testcontainers` lifecycle, causing intermittent CI-only "connection refused" failures. Fixed via a static-initializer singleton container. That fix then exposed a second, larger issue — several IT classes only cleaned up their own tables (or nothing at all), relying on the old buggy restart behavior for a fresh-enough DB — fixed by centralizing a comprehensive FK-safe wipe in `IntegrationTestBase`'s `@BeforeEach`. That then exposed a connection-pool-exhaustion issue, fixed by capping the test-profile Hikari pool size.
5. A `LazyInitializationException` in the new `PlayerGameStatsRepositoryIT` test (asserting on a `LAZY` association outside its persistence context) — fixed by asserting on a directly-mapped column instead.

## Backtesting (on `phase-3/backtest-validation`, not pushed)

Closes the dev plan's last Phase 3 checklist item: does the engine's score actually predict real fantasy output, not just look internally consistent?

- New `analytics.backtest` package: `PearsonCorrelation` (hand-rolled, pure, unit-tested independently), `BacktestResult`, `BacktestService` — regenerates every week of a season's `START_SIT` recommendations, then correlates predicted score against real `player_game_stats.fantasy_points_ppr`, overall and per position.
- **Injury-overridden recommendations are excluded from the correlation**, reported separately (`excludedDueToInjuryOverride`) — `InjuryFactorCalculator` uses the player's *current* status, not a historical one, so applying today's status to a months-old game would just inject synthetic noise.
- New endpoint: `POST /api/recommendations/backtest?season=`
- `BacktestServiceIT` uses deliberately minimal fixtures (each test player has exactly one factor) so the resulting correlation is an exact, assertable number, plus a fixture player with a real `OUT` status and real box-score points on file to prove the exclusion path actually excludes.

See `CLAUDE.md`'s new "Backtesting" section for the full design writeup.

## Manual verification — a real performance bug, and a real (important) finding about the model

**Performance bug found and fixed**: `BacktestService.runBacktest` was `@Transactional`, and it calls `StartSitRecommendationService.computeForWeek` (also `@Transactional`) 18 times in a loop. Spring's default propagation joined all 18 weeks into one giant Hibernate session/transaction, and a full-season run against real 2025 data took **2 hours 9 minutes** before finishing. Removed `@Transactional` from `runBacktest` so each week gets its own transaction (matching `computeForWeek`'s own boundary) — the same run then took **~18 minutes**, a ~7x improvement. Still slower than ideal (likely N+1-style per-player-per-factor queries inside `gatherFactors`, repeated across ~15,000 player-weeks) but not blocking, since this is an occasional analysis endpoint, not a hot path — flagged as a real follow-up optimization if it needs to run often.

**The actual backtest result against the full 2025 season is the headline finding**: 15,139 recommendations generated, 5,736 matched against real box scores, and the predicted score's correlation with actual fantasy output came out **essentially zero** (-0.02 to +0.06 depending on position). Independently cross-checked via Postgres's own `corr()` aggregate — exact match on the matched-pair count, confirming this is a real result, not a bug in the join/exclusion logic. This is the expected, correct outcome of a `v1` model whose factor weights were hand-picked and never tuned against outcomes before this ran for the first time — exactly what `docs/data-sourcing-and-algorithms.md` §2.5 anticipated needing eventually. **Don't casually reweight the factors based on this alone** — real tuning is a deliberate follow-up effort, not a quick guess.

## What remains (lower priority, not blocking)

- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one)
- Backtest performance (N+1 query pattern, ~18 min for a full season) — not urgent, occasional endpoint
- **Real, substantive next step surfaced by this work**: the scoring engine's `v1` weights need actual tuning against backtest data before the recommendations are more than "internally consistent." Worth a dedicated future effort (grid search / simple regression against the same factor inputs, per the docs' own "where ML could fit later" note) rather than guessing new numbers.

## Recommended next steps

1. Commit and push `phase-3/backtest-validation`, let CI verify.
2. Once merged, Phase 3 is fully complete per its original checklist. Ask the user: pursue weight tuning (the real finding above), move to waiver/trade (reuse the same factor engine, per `docs/development-plan.md`), or something else. To see `VEGAS`/`WEATHER` factors appear in a start/sit demo, need to wait for the 2026 season to actually start.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, `phase-3/start-sit-scoring-engine`, and `phase-3/player-trending-endpoint` still exist on origin from prior slices (not yet deleted — user has deferred that cleanup each time).

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified, including a substantially more robust IT test suite after this phase's fixes. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas"). The Docker Desktop Testcontainers context mismatch gotcha is also documented there and was worked around previously, not fixed at the system level — either may resurface on a fresh machine/session.
