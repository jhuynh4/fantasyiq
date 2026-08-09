# Current Work

## Status: no task in progress — between PRs, awaiting direction

The last PR (`phase-2/hardening`) merged to `main` and this session ended right after. No branch is currently checked out for active work; `main` is up to date locally.

## What's been completed (Phase 1 + Phase 2, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion (ESPN rosters → `players` table, search/profile endpoints).

**Phase 2** — in order merged:
1. Games/schedule ingestion (regular season only)
2. Injury ingestion (thin ESPN data: status + date only)
3. Player-game-stats ingestion (QB/RB/WR/TE box scores, self-computed fantasy points)
4. Hardening bundle: `ingestion_runs` audit logging, Resilience4j retry/circuit breaker, `@Scheduled` cron jobs, structured JSON logging with correlation ids

See `CLAUDE.md` for the durable architectural knowledge from all of this. This file is just "where did we leave off."

## What remains in Phase 2 (discussed, not started)

Two options were on the table when the session ended, **no decision made**:

1. **`defense_vs_position_stats`** — now unblocked since `player_game_stats` exists to compute it from. Schema for this table doesn't exist yet (would need a new migration). No design discussion happened yet on how it'd be computed/aggregated.
2. **`betting_lines` (Odds API) + `weather_forecasts` (OpenWeatherMap)** — both blocked on the user obtaining free-tier API keys from those two services. Not yet requested/obtained as of end of session.

Also still open, lower priority:
- WireMock contract test coverage for `EspnInjuryProvider` (only `EspnStatsProvider` has one — scoped down deliberately given the size of the hardening PR)

## Recommended next steps

Ask the user which of the two Phase 2 options above they want to tackle first, or whether to pause Phase 2 and move to Phase 3 (analytics/scoring engine) instead — that decision was never made explicit, just implicitly "keep going through Phase 2" up to this point.

If continuing Phase 2 with `defense_vs_position_stats`: this needs a design pass first (what table shape, how "defense vs position" gets aggregated from `player_game_stats` rows) before writing code — same pattern as the player-game-stats schema decision (present the plan, get buy-in, then build).

## No known blockers or in-flight problems

Everything currently on `main` is merged, CI-green, and was manually verified end-to-end against real ESPN data (see `CLAUDE.md`'s "hard-won ESPN quirks" section for what that verification surfaced). Local dev environment quirks (JDK 25 Gradle bug, Docker Desktop Testcontainers context mismatch) are documented in `CLAUDE.md` and were worked around, not fixed at the system level — a fresh machine/session may hit them again.
