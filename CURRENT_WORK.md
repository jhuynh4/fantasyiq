# Current Work

## Status: Phase 6 first slice (ingestion metrics) merged — no active branch

`phase-6/ingestion-metrics` (Micrometer instrumentation for ingestion job duration/success) is merged to `main` (PR #17) and the local branch is cleaned up. Everything described below is on `main`.

## What's been completed (Phases 1-5, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2** — games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle, `defense_vs_position_stats`, `weather_forecasts`, `betting_lines`.

**Phase 3** — start/sit recommendation engine (6 factors), player trending endpoint, backtest validation, weight tuning + recent-performance factor (took real predictive correlation from `0.0137` to `0.306`).

**Phase 4** — Redis caching (`GET /recommendations/start-sit` 177ms → 4.6ms avg, `GET /players/{id}` 25.5ms → 3.6ms avg), Bucket4j rate limiting on `/api/auth/**`, Bean Validation on previously-unconstrained request params.

**Phase 5, first slice** — trade analyzer (`POST /api/trades/analyze`): rest-of-season value comparison reusing three of the six factor calculators plus a positional replacement-level adjustment. ~3.3s per request (known, deliberately-accepted N+1 cost), not batched yet.

Full writeups for all of the above in `CLAUDE.md`.

## Phase 6, first slice: Ingestion metrics (PR #17, merged)

- Closes the "Micrometer metrics" item off `docs/development-plan.md`'s Phase 6 checklist. **Investigated what was already live before writing anything** — request latency, cache hit ratio, and external API error rate turned out to already be auto-instrumented for free (Spring Boot + Resilience4j autoconfiguration, dependencies present since Phase 0), confirmed by checking `/actuator/prometheus` directly rather than assuming.
- **The one real gap**: ingestion job duration/success had no *live* metric, only the `ingestion_runs` audit table (queryable after the fact, not watchable in real time). Fixed by adding a `Timer` (`ingestion.run.duration`, tagged by `source`/`outcome`) to `IngestionRunService.track(...)` — the single choke point every ingestion/computation job already flows through, so no per-job wiring was needed.
- Also enabled p50/p95/p99 percentile publishing for `http.server.requests` and the new timer (not on by default). Client-side percentiles, not histogram buckets — single-instance deployment, no cross-instance aggregation to get right.
- **Verified live**: triggered a real failing ESPN call and a real successful recommendation-generation job, confirmed both produced correctly tagged, queryable metrics on `/actuator/prometheus`.
- `IngestionRunServiceTest` (new) asserts against a real `SimpleMeterRegistry`, not a mocked one.

Full design rationale in `CLAUDE.md`'s "Metrics" section.

**The AWS-dependent rest of Phase 6** (CloudWatch log shipping, a dashboard, alarms, the runbook doc) waits for Phase 7's infrastructure to actually exist — not started.

## What remains (lower priority, not blocking)

- Trade analyzer performance (N+1 query pattern in `computeReplacementLevels`, ~3.3s per request) — not urgent, occasional endpoint
- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one)
- Backtest performance (N+1 query pattern inside `gatherFactors`, ~18 min for a full season) — not urgent, occasional endpoint
- `MatchupFactorCalculator`'s long-run uniform averaging and the weak `USAGE` factor remain real, un-investigated hypotheses if further model improvement is wanted later

## Recommended next steps

Three real directions from here, none started yet:

1. **Batch the trade analyzer's replacement-level computation** if the 3.3s cost turns out to matter in practice.
2. **Move to Phase 7** — AWS deployment & CI/CD (Dockerfile, Terraform, ECS, Secrets Manager) — this is what unlocks the rest of Phase 6 (CloudWatch shipping, dashboards, alarms), since those genuinely need real infrastructure to exist first.
3. **Phase 8** — frontend, lower priority per the dev plan, likely follows Phase 7.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, `phase-3/start-sit-scoring-engine`, `phase-3/player-trending-endpoint`, and `phase-3/backtest-validation` still exist on origin from prior slices (deferred cleanup, unchanged). `phase-6/ingestion-metrics` has been deleted both locally and remotely.

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas").
