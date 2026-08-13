# Current Work

## Status: player trending endpoint built, not yet compiled/tested — awaiting a build

Branch `phase-3/player-trending-endpoint` adds `GET /api/players/{id}/trending`, per user's "whatever's best" delegation after the start/sit merge — this was the remaining item from Phase 3's original checklist that adds real, non-duplicate value (see below for why `GET /api/rankings` was skipped). Written but not yet built/tested.

## What's been completed (Phase 1 + Phase 2, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2 — fully complete**: games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle (audit logging, Resilience4j, scheduling, structured logging), `defense_vs_position_stats` (computed), `weather_forecasts` (OpenWeatherMap), `betting_lines` (The Odds API).

## Phase 3, first slice — start/sit recommendation engine (merged)

Scope was deliberately narrowed to **start/sit only** — waiver, trade, and rankings all reuse the same factor engine per `docs/development-plan.md`, so proving the engine works on the simplest case first made sense before generalizing.

- `V13`/`V14` migrations: `recommendations` + `recommendation_factors` tables
- New `domain.recommendation` package (entities + reconciliation), new `analytics.scoring` package (`FactorResult` + five pure calculators: Matchup, VegasImpliedTotal, Weather, Injury, UsageTrend), new `analytics.startsit.StartSitRecommendationService` (composes calculators into a scored, explainable recommendation per player, skips players with zero available factors)
- New endpoints: `POST /api/recommendations/generate?season=&week=`, `GET /api/recommendations/start-sit?season=&week=&position=`
- `RedZoneFactorCalculator`/`StrengthOfScheduleFactorCalculator` deliberately dropped from the original plan (ESPN never provides red zone touches; SOS is a better fit for a future waiver/trade slice) — `docs/development-plan.md` and `docs/data-sourcing-and-algorithms.md` updated to say so explicitly.

See `CLAUDE.md`'s "Start/sit recommendation engine" section for the full design writeup, and its "Testing strategy" section for the test-infrastructure fixes below.

**Manually verified** against real 2025 season data: generated 760 real recommendations for week 8, spot-checked the math (matchup/usage narratives, score = sum of contributions, `OUT` correctly forcing a `-1000`-dominated score and `LOW` confidence, `QUESTIONABLE` applying a smaller penalty without forcing confidence down), confirmed idempotent regeneration.

**Real bugs found and fixed during this slice** (beyond the feature itself):
1. An `ingestion_runs.source` audit constant exceeded its `VARCHAR(30)` column.
2. `recommendation_factors.factor_weight` was sized for a fractional 0..1 weight; the calculators use point-scale weights instead — widened via `V14`.
3. Both of the above (and other unrelated exceptions) were surfacing as a misleading `403` instead of a real status, due to Spring Security not permitting the internal `/error` dispatch — `GlobalExceptionHandler` now has a catch-all handler returning a real `500` and logging server-side.
4. **A significant, unrelated pre-existing bug surfaced and fixed**: `IntegrationTestBase`'s shared Testcontainers Postgres was tied to `@Container`/`@Testcontainers`' per-class lifecycle, so whichever IT test class finished first stopped the container out from under every sibling class still to run — intermittent CI-only "connection refused" failures. Fixed by starting the container once via a static initializer (the correct Testcontainers "singleton container" pattern). Fixing *that* then exposed a second, larger issue: several pre-existing IT test classes only cleaned up the tables they personally touched (or nothing at all), silently relying on the old buggy container-restart behavor for a fresh-enough DB — once the container stopped restarting, that caused FK constraint violations and wrong row counts across half a dozen unrelated test classes. Fixed by centralizing a comprehensive, FK-safe table wipe in `IntegrationTestBase`'s own `@BeforeEach`, which now runs before any subclass's own. Finally, a connection-pool-exhaustion issue surfaced once *that* was fixed (many distinct cached Spring test contexts, each with a full-size Hikari pool, against the one shared Postgres) — fixed by capping `application-test.yml`'s Hikari pool size to 3.

## Player trending endpoint (on `phase-3/player-trending-endpoint`, not pushed)

- New `PlayerGameStatsRepository.findTop4ByPlayerOrderByGame_SeasonDescGame_WeekDesc` — the one genuinely new piece of logic here; unlike every other query in this codebase it has **no season/week boundary**, since trending means "what's this player's usage doing right now," not tied to one week's recommendation. Covered by a new `PlayerGameStatsRepositoryIT` proving it correctly crosses a season boundary (fixtures span two distinctive fake seasons, 2098/2099).
- `PlayerController.trending` reuses the already-tested `UsageTrendFactorCalculator` directly against those 4 games — no new scoring logic, pure wiring. Matches the established convention of not writing controller-level tests (only `AuthControllerIT` has one, everything else is tested at the service/repository layer).
- **`GET /api/rankings?position=&scoring=ppr` deliberately not built** — as specified in the original plan ("positional rankings from the same factor engine"), it would just re-sort the exact data `GET /api/recommendations/start-sit` already returns, so it'd be a near-duplicate route rather than new value. Told the user this reasoning rather than silently skipping it; revisit if a genuinely different ranking basis (e.g. season-to-date actual production) is wanted later.

## What remains (lower priority, not blocking)

- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one)

## Recommended next steps

1. User builds `phase-3/player-trending-endpoint` via IntelliJ, then manual-test against real data (e.g. a player with 4+ ingested games from the 2025 season) before push.
2. Once merged: waiver and trade are the remaining Phase-3-adjacent features that reuse the same factor engine, per `docs/development-plan.md`. To see `VEGAS`/`WEATHER` factors appear in a start/sit demo, need to wait for the 2026 season to actually start (games close enough for weather's 5-day window, and matchup/usage data to exist for that season).

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, and `phase-3/start-sit-scoring-engine` still exist on origin from prior slices (not yet deleted — user has deferred that cleanup each time).

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified, including a substantially more robust IT test suite after this slice's fixes. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas"). The Docker Desktop Testcontainers context mismatch gotcha is also documented there and was worked around previously, not fixed at the system level — either may resurface on a fresh machine/session.
