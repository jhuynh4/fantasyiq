# ADR 0002: Free-tier-only external data sources

## Status
Accepted

## Context
Development cost is a hard constraint for this project — the goal is a
production-quality build at as close to $0 recurring cost as possible.
SportsDataIO offers the cleanest schema and best injury data (including
practice participation) but has no perpetual free tier.

## Decision
- Stats/rosters/schedules: ESPN's public (undocumented) API, live; nflverse
  data for historical backtesting.
- Injuries: ESPN's public API (thinner signal — no practice participation).
- Betting lines: The Odds API, free tier.
- Weather: OpenWeatherMap, free tier.
- Trending signal: Sleeper's public API.

## Alternatives Considered
- SportsDataIO paid tier — rejected for recurring cost; revisit only if ESPN's
  injury signal proves genuinely insufficient for the scoring engine.
- Scraping additional sites for richer injury detail — rejected: fragile,
  higher maintenance burden than the value it would add at this stage.

## How I'll know this was the right call
- ESPN adapter uptime/error rate over a full month of scheduled ingestion runs
  (tracked via `ingestion_runs` table) — target: no more than a handful of
  failed runs per week before retry/circuit-breaker kicks in.
- Backtest (Phase 3) comparing recommendation quality using ESPN-derived
  injury status alone vs. a manually spot-checked "what actually happened"
  for a sample of known injury situations from a past season.

## Result
Pending — fill in once Phase 2 (full ingestion pipeline) has run against a
real span of the season and `ingestion_runs` has enough history to evaluate.
If ESPN's injury signal turns out to meaningfully hurt recommendation quality,
the adapter-pattern isolation (§4.2 of the system design doc) means swapping
in a paid source later is a contained change, not a rewrite.
