# Current Work

## Status: `betting_lines` built, manually verified against real data, ready to push for CI — Phase 2 complete

Branch `phase-2/betting-lines` has the full slice: migration, entities, adapter, mapper, ingestion service, controller, unit/contract/integration tests. Built clean via IntelliJ and manually verified end-to-end against the live app, a real `ODDS_API_KEY`, and real ingested 2026-season games. Not yet committed/pushed. Once this merges, **Phase 2 is fully complete** — all four originally-planned data sources (ESPN stats/injuries, OpenWeatherMap, The Odds API) are ingesting real data.

## What's been completed (Phase 1 + Phase 2, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion (ESPN rosters → `players` table, search/profile endpoints).

**Phase 2** — in order merged:
1. Games/schedule ingestion (regular season only)
2. Injury ingestion (thin ESPN data: status + date only)
3. Player-game-stats ingestion (QB/RB/WR/TE box scores, self-computed fantasy points)
4. Hardening bundle: `ingestion_runs` audit logging, Resilience4j retry/circuit breaker, `@Scheduled` cron jobs, structured JSON logging with correlation ids
5. `defense_vs_position_stats` — computed (not ingested) aggregation ranking each team's defense against QB/RB/WR/TE by fantasy points allowed, per week.
6. `weather_forecasts` ingestion (OpenWeatherMap) — dome-skip, `games.is_dome` backfill, past/future kickoff window guard.

**Not yet merged, on `phase-2/betting-lines` (not yet pushed):**
7. `betting_lines` ingestion (The Odds API). `V12` migration creates `betting_lines` (two rows per game, one per team, upserted by `(game_id, team_id)`). New `ingestion.odds` package (`OddsProvider`/`TheOddsApiProvider`, own `oddsApi` Resilience4j instance, own `OddsUnavailableException`). `OddsIngestionService` takes **no season/week params** — unlike every other job, The Odds API has no such concept, so it just ingests "whatever's currently on the board" and matches each returned game to ours by team name + closest kickoff (3-day tolerance), skipping unresolvable ones. New endpoint `POST /api/odds/ingest`. See `CLAUDE.md`'s "Betting-line ingestion" section for the full design writeup.

See `CLAUDE.md` for the durable architectural knowledge behind all of this. This file is just "where did we leave off."

## Manual verification (betting_lines)

Tested against the live app with a real `ODDS_API_KEY` and real ingested 2026 season games (272 games, current season):
- A single live call returned lines for the **entire season already** (544 rows = 272 games × 2 teams), which was initially surprising for a "current odds" endpoint — but the per-week row counts turned out to exactly mirror the real NFL bye-week pattern (32 → drops to 26–30 during bye weeks → back to 32), the same sanity check that validated `defense_vs_position_stats` and gave strong confidence the team/game matching and implied-total math are correct against real data.
- Spot-checked actual spread/over-under values across different weeks — genuinely different per game, not duplicated/stale data.
- Re-ran the endpoint a second time and confirmed the row count stayed at 544 (idempotent upsert, no duplication).

## Phase 2 is now feature-complete

All four data sources from the original plan are ingesting real data: ESPN (rosters/schedules/injuries/box scores), computed defense-vs-position stats, OpenWeatherMap (forecasts), and The Odds API (betting lines).

Still open, lower priority:
- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one — `EspnStatsProvider`, `OpenWeatherProvider`, and `TheOddsApiProvider` all have one)

## Recommended next steps

1. Commit and push `phase-2/betting-lines`, let CI verify.
2. Once merged: Phase 2 is done. Ask the user whether to move to Phase 3 (analytics/scoring engine — start/sit, waiver, trade recommendations) or clean up the `EspnInjuryProvider` test-coverage gap first.

Remote branches `phase-2/defense-vs-position-stats` and `phase-2/weather-forecasts` still exist on origin from prior slices (not yet deleted — user has deferred that cleanup each time).

## No known blockers or in-flight problems

Everything on `main` is merged, CI-verified, and manually verified end-to-end against real ESPN and OpenWeatherMap data. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas"). The Docker Desktop Testcontainers context mismatch gotcha is also documented there and was worked around previously, not fixed at the system level — either may resurface on a fresh machine/session.
