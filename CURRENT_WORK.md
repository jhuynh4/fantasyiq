# Current Work

## Status: Phase 3 first slice (start/sit) built, manually verified against real data, ready to push for CI

Branch `phase-3/start-sit-scoring-engine` has the full first slice of Phase 3, built clean via IntelliJ and manually verified end-to-end against the live app with real 2025 season data (games, injuries, player-game-stats, defense-vs-position). Not yet committed/pushed.

## What's been completed (Phase 1 + Phase 2, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2 — fully complete**, all merged: games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle (audit logging, Resilience4j, scheduling, structured logging), `defense_vs_position_stats` (computed), `weather_forecasts` (OpenWeatherMap), `betting_lines` (The Odds API). See `CLAUDE.md` for the durable detail on all of this — this file is just "where did we leave off."

## Phase 3, first slice — start/sit recommendation engine (on `phase-3/start-sit-scoring-engine`, not pushed)

Scope was deliberately narrowed from the full `docs/development-plan.md` Phase 3 checklist to **start/sit only** — waiver, trade, and rankings all reuse the same factor engine per the plan, so proving the engine works on the simplest case first made sense before generalizing.

- `V13` migration: `recommendations` (upserted by `player`+`season`+`week`+`type`) + `recommendation_factors` (child table, `ON DELETE CASCADE`, fully replaced on each regeneration)
- `V14` migration: widens `recommendation_factors.factor_weight` from `V13`'s `NUMERIC(5,4)` to `NUMERIC(6,2)` — found during manual testing (see below)
- New `domain.recommendation` package: `Recommendation`/`RecommendationFactor` entities + `RecommendationReconciliationService`
- New `analytics.scoring` package: `FactorResult` + five pure calculators — `MatchupFactorCalculator`, `VegasImpliedTotalFactorCalculator`, `WeatherFactorCalculator`, `InjuryFactorCalculator`, `UsageTrendFactorCalculator`
- New `analytics.startsit.StartSitRecommendationService` — composes the calculators per player, sums contributions into a score, skips players with zero available factors entirely
- New `POST /api/recommendations/generate?season=&week=` and `GET /api/recommendations/start-sit?season=&week=&position=`
- **A factor deliberately dropped**: `RedZoneFactorCalculator` from the original plan — `player_game_stats.red_zone_touches` is always `NULL` from ESPN, no real signal to compute. `docs/development-plan.md` and `docs/data-sourcing-and-algorithms.md` were both updated to document this explicitly, per explicit user request.
- `ArchitectureRulesTest`'s `analyticsMustNotDependOnApiLayer` rule had its `allowEmptyShould(true)` removed now that `analytics` has real classes — matches the exact precedent already set for `domain`/`ingestion` earlier in the project.

See `CLAUDE.md`'s "Start/sit recommendation engine" section for the full design writeup.

## Manual verification — found and fixed three real bugs

Tested against the live app with real 2025 season data (games, injuries, and the full season's player-game-stats already ingested; computed `defense_vs_position_stats` for weeks 1-7 to build up matchup history, then generated recommendations for week 8):

- **Bug 1**: `StartSitRecommendationComputationService`'s audit-log `SOURCE` constant (`"COMPUTED_START_SIT_RECOMMENDATIONS"`, 35 chars) exceeded `ingestion_runs.source`'s `VARCHAR(30)`, so the very first audit-row insert failed before the real computation ever ran. Fixed by shortening to `"COMPUTED_START_SIT"`.
- **Bug 2**: `recommendation_factors.factor_weight` was `NUMERIC(5,4)` in `V13` (assumed a fractional 0..1 weight, matching the original `docs/system-design.md` sketch), but the actual calculators use point-scale weights (e.g. `MatchupFactorCalculator`'s `WEIGHT=25`), overflowing that precision on the first real insert. Fixed via `V14` migration widening to `NUMERIC(6,2)`.
- **Bug 3 (bigger, pre-existing)**: both of the above failures were surfacing to the client as a misleading `403` instead of a real error status — the same unpermitted-`/error`-dispatch masking already found once during weather testing and narrowly fixed for vendor-unavailable exceptions only. Since it bit a second, completely unrelated exception type (`DataIntegrityViolationException`) and cost real debugging time before the real error was found (via checking `ingestion_runs.error_message` and eventually the IntelliJ console), **`GlobalExceptionHandler` now has a catch-all `@ExceptionHandler(Exception.class)`** returning `500` and logging the real exception server-side. This should prevent this exact class of confusion from recurring for any future uncaught exception.

After both schema fixes: generated 760 real recommendations for week 8 of the 2025 season. Spot-checked results: Ja'Marr Chase topped the WR list with a real, sensible signal ("targets up from 9.0 to 17.5 over trailing games" + a favorable matchup rank); score fields matched the sum of their factor contributions in every case checked; a real `OUT` player's score was correctly dominated by the `-1000` injury penalty with confidence forced to `LOW`; a `QUESTIONABLE` player got a smaller `-10` penalty without forcing confidence down (matches the "only OUT/IR forces LOW" design). No `VEGAS`/`WEATHER` factors appeared for this 2025 historical data, as expected — no `betting_lines`/`weather_forecasts` rows exist for a season that already happened (Odds API only has current lines, weather ingestion now correctly skips past kickoffs). Re-ran generation a second time and confirmed the row count stayed at 760 (idempotent upsert).

## Recommended next steps

1. Commit and push `phase-3/start-sit-scoring-engine`, let CI verify.
2. Once merged: waiver, trade, and rankings all reuse this same factor engine (per `docs/development-plan.md`) — natural next slices. To get `VEGAS`/`WEATHER` factors showing up in a real demo, would need to wait for the 2026 season to actually start (games close enough for weather's 5-day window, and matchup/usage data to exist for that season) or re-test once it does.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, and `phase-2/betting-lines` still exist on origin from prior slices (not yet deleted — user has deferred that cleanup each time).

## No known blockers or in-flight problems

Everything on `main` is merged, CI-verified, and manually verified end-to-end against real ESPN, OpenWeatherMap, and The Odds API data. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas"). The Docker Desktop Testcontainers context mismatch gotcha is also documented there and was worked around previously, not fixed at the system level — either may resurface on a fresh machine/session.
