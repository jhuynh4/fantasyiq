# FantasyIQ

A fantasy football companion analytics platform. It doesn't replace Sleeper/ESPN/Yahoo — it tells you *why* to start a player, pick up a waiver add, or accept a trade, using real matchup, usage, injury, and betting-market data.

This repo is being built as a solo-developer production-style project — see `/docs` for the full system design, development plan, PRD, and data-source integration docs.

## Status

**Phase 0 — Foundations.** Project skeleton, local dev environment, CI, and architectural guardrails are in place. No features yet — see the development plan for what's next.

## Architecture at a Glance

Modular monolith (Spring Boot), organized feature-first:

```
com.fantasyiq
├── ingestion/     external API adapters (stats, injuries, odds, weather, trending) + schedulers
├── domain/        core entities: player, team, game, stats
├── analytics/      scoring engine: start/sit, waiver, trade, rankings
├── api/           controllers, DTOs, error handling
├── auth/          Spring Security + JWT
├── cache/         Redis config and cache key strategy
└── common/        shared value objects, config
```

Module boundaries (e.g. "ingestion must never depend on api") are enforced by `ArchitectureRulesTest` — this test is meant to fail loudly the moment a boundary gets crossed, so treat a failure there as a real design smell, not a test to silence.

## Running Locally

**Prerequisites:** JDK 21, Docker (for Postgres + Redis).

```bash
# 1. Start Postgres + Redis
docker compose up -d

# 2. Run the app (Flyway migrations run automatically on startup)
./gradlew bootRun

# 3. Confirm it's alive
curl http://localhost:8080/actuator/health
```

You should see Postgres seeded with all 32 NFL teams (via `V2__seed_teams.sql`) once the app starts.

### Running tests

```bash
./gradlew test
```

Integration tests extend `IntegrationTestBase`, which spins up a real Postgres via Testcontainers — no mocking of the database layer. Requires Docker to be running.

## Gradle Wrapper Note

This repo expects a Gradle wrapper (`gradlew` / `gradlew.bat` / `gradle/wrapper/`). If you're starting from this scaffold before the wrapper files are generated, run once (requires a local Gradle install or an IDE with Gradle support):

```bash
gradle wrapper --gradle-version 8.10
```

After that, everyone else just uses `./gradlew` — no local Gradle install required.

## Configuration & Secrets

Real API keys (`ODDS_API_KEY`, `OPENWEATHER_API_KEY`, `JWT_SECRET`) are read from environment variables (see `application.yml`). Never commit real values — copy `.env.example` to `.env` for local use (not loaded automatically; wire it up via your shell or IDE run config).

## Data Sources

| Domain | Source | Notes |
|---|---|---|
| Rosters, stats, schedules | ESPN public API | No key required; see `docs/data-source-integration.md` |
| Historical/backtest data | nflverse | Free, batch files, used for Phase 3 backtesting |
| Injuries | ESPN public API | Thin signal — a known trade-off of the free-tier choice |
| Betting lines | The Odds API | Free tier, requires `ODDS_API_KEY` |
| Weather | OpenWeatherMap | Free tier, requires `OPENWEATHER_API_KEY`, outdoor games only |
| Trending signal | Sleeper public API | No key required |

## Docs

- `docs/system-design.md` — full architecture, phases, schema, AWS mapping
- `docs/development-plan.md` — task-level phased execution plan
- `docs/prd.md` — feature specs, personas, acceptance criteria
- `docs/data-sourcing-and-algorithms.md` — waiver/scoring algorithm design
- `docs/data-source-integration.md` — real endpoints, ID reconciliation strategy
- `docs/free-tier-guide.md` — keeping this at (near) $0 to build and run
- `docs/measuring-decisions.md` — how to benchmark and justify each tech choice
- `docs/adr/` — architecture decision records (create as you make real decisions)

## License

Personal/portfolio project — no license applied yet.
