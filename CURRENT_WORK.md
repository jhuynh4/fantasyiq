# Current Work

## Status: `weather_forecasts` built, manually verified against real data, ready to push for CI

Branch `phase-2/weather-forecasts` has the full slice: migrations, entities, adapter, mapper, ingestion service, controller, unit/contract/integration tests, plus a real-bug fix found during manual testing (see below). Built clean via IntelliJ (Gradle couldn't run locally on this machine this session — see `CLAUDE.md`'s "Local environment gotchas") and manually verified end-to-end against the live app, a real `OPENWEATHER_API_KEY`, and real ingested 2025 season data. Not yet committed/pushed.

## What's been completed (Phase 1 + Phase 2, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion (ESPN rosters → `players` table, search/profile endpoints).

**Phase 2** — in order merged:
1. Games/schedule ingestion (regular season only)
2. Injury ingestion (thin ESPN data: status + date only)
3. Player-game-stats ingestion (QB/RB/WR/TE box scores, self-computed fantasy points)
4. Hardening bundle: `ingestion_runs` audit logging, Resilience4j retry/circuit breaker, `@Scheduled` cron jobs, structured JSON logging with correlation ids
5. `defense_vs_position_stats` — computed (not ingested) aggregation ranking each team's defense against QB/RB/WR/TE by fantasy points allowed, per week.

**Not yet merged, on `phase-2/weather-forecasts` (not yet pushed):**
6. `weather_forecasts` ingestion (OpenWeatherMap). `V10` migration seeds `stadium_locations` with real lat/long + `is_dome` for all 32 teams. `V11` migration creates `weather_forecasts`. New `domain.team.StadiumLocation` (static seeded reference data, no reconciliation service). New `domain.stats.WeatherForecast` + `WeatherForecastReconciliationService` (upserts by `Game`, no external ref to key off). New `ingestion.weather` package (`WeatherProvider`/`OpenWeatherProvider`, its own `weatherApi` Resilience4j instance, own `WeatherUnavailableException`). `WeatherIngestionService` also backfills `games.is_dome` (a column that's existed since `V4`, unpopulated until now) from the stadium lookup. New endpoint `POST /api/weather/ingest?season=&week=`. See `CLAUDE.md`'s "Weather ingestion" section for the full design writeup.

See `CLAUDE.md` for the durable architectural knowledge behind all of this. This file is just "where did we leave off."

## Manual verification (weather_forecasts) — found and fixed a real bug

Tested against the live app with a real `OPENWEATHER_API_KEY` and real ingested 2025 season games:
- Dome-team games (e.g. `NO`) correctly skip the OpenWeatherMap call and still backfill `games.is_dome = true`.
- A real outdoor-game call to OpenWeatherMap succeeded and returned real temperature/wind/precipitation data.
- **Bug found**: the original window check only skipped kickoffs too far in the *future*, never ones already in the *past*. Since OpenWeatherMap is a forecast API with no historical data, calling it for an already-played 2025 game silently returned *today's* weather, which got stored as if it were that game's forecast — wrong data, no error. Fixed by also skipping any kickoff that's already passed (`WeatherIngestionService.doIngestForecasts`), added a regression test (`WeatherIngestionServiceIT.skipsOutdoorGamesWhoseKickoffAlreadyPassed`), and deleted the 10 bad rows the bug had written to the local dev DB.
- Also found (same manual-testing pass): vendor-unavailable exceptions (`EspnUnavailableException`, `WeatherUnavailableException`) were propagating uncaught past `GlobalExceptionHandler`, hitting Spring's default `/error` dispatch — which `SecurityConfig` never explicitly permits — and surfacing to the client as a generic `403` instead of a real status. Fixed by adding an explicit `@ExceptionHandler` for both exception types in `GlobalExceptionHandler`, mapping them to `503 SERVICE_UNAVAILABLE`. This was a **pre-existing gap affecting ESPN failures too**, not something new to weather — it just never surfaced before since ESPN calls always eventually succeeded during prior testing.

## No API keys for betting lines yet

`OPENWEATHER_API_KEY` is now set (user's own key) and weather ingestion has been verified against live data — this item is no longer blocked.

`betting_lines` (Odds API) not started — fully blocked on `ODDS_API_KEY`, which doesn't exist yet.

Also still open, lower priority:
- WireMock contract test coverage for `EspnInjuryProvider` (only `EspnStatsProvider` and now `OpenWeatherProvider` have one)

## Recommended next steps

1. Commit and push `phase-2/weather-forecasts`, let CI verify (local Gradle was blocked this session, so CI is the first real automated-test run this code gets).
2. Once CI is green and the PR is merged, sync `main` and clean up the branch (standard pattern).
3. After that: `betting_lines` is the only remaining Phase 2 item, fully blocked on `ODDS_API_KEY`.

Remote branch `phase-2/defense-vs-position-stats` still exists on origin from the prior slice (not yet deleted — user deferred that cleanup).

## No known blockers or in-flight problems

Everything previously merged to `main` is CI-green and manually verified end-to-end against real ESPN data. Local Gradle CLI was blocked this session by a JVM loopback-socket issue (worked around by building via IntelliJ instead — see `CLAUDE.md`). The Docker Desktop Testcontainers context mismatch gotcha is also documented in `CLAUDE.md` and was worked around, not fixed at the system level — a fresh machine/session may hit either again.
