# FantasyIQ — Development Plan

A task-level execution plan, sequenced so every step ends in something runnable. Each phase lists tasks as checkboxes, a "definition of done," and the concrete skills it exercises. Estimates assume solo, part-time (~10–15 hrs/week) work; adjust to your pace.

**How to use this:** work top to bottom. Don't start a phase until the previous phase's "definition of done" is met — the temptation to jump ahead to the scoring engine or AWS before the pipeline is solid is exactly what turns portfolio projects into unfinished ones.

---

## Phase 0 — Foundations
**Estimate: 1–2 weeks**

- [ ] Init Git repo, `.gitignore`, branch protection on `main`
- [ ] Spring Boot 3.x project (Java 21), Gradle build, package skeleton from the design doc's §4.1
- [ ] `docker-compose.yml`: Postgres 16 + Redis 7, named volumes, healthchecks
- [ ] Flyway wired up; first migration creates an empty baseline
- [ ] `application.yml` per profile (`local`, `test`, `prod`) — no secrets committed, use env vars/`application-local.yml` gitignored
- [ ] GitHub Actions workflow: `./gradlew build test` on every push/PR
- [ ] ArchUnit test asserting the module boundaries from §4.1 (e.g. `ingestion` must not depend on `api`) — write this now, even with almost-empty packages, so it fails loudly later if boundaries erode
- [ ] README v1: what the project is, how to run it locally with Docker Compose

**Definition of done:** `docker compose up` + `./gradlew bootRun` gives you a running app hitting local Postgres/Redis, and CI is green on an empty-ish repo.

**Skills exercised:** Docker, CI basics, migration discipline, architectural guardrails.

---

## Phase 1 — Core Domain, Auth, First Vertical Slice
**Estimate: 2–3 weeks**

- [ ] `users` + `refresh_tokens` tables (Flyway migration)
- [ ] Spring Security config: JWT access + refresh token flow, password hashing (BCrypt)
- [ ] `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`
- [ ] `teams` and `players` tables (Flyway migration), seed `teams` with all 32 NFL teams
- [ ] Pick **one** stats provider (§5) and build `StatsProvider` interface + one implementation
- [ ] Ingestion job (manually triggered endpoint first, scheduled later) that pulls players/rosters and upserts into `players` by `external_ref`
- [ ] `GET /api/players/search?q=`, `GET /api/players/{id}` — real data, not mocked
- [ ] Unit tests: auth service, player mapping logic
- [ ] Integration tests: Testcontainers Postgres, verify upsert idempotency (run ingestion twice, assert no duplicates)
- [ ] Postman/Bruno collection or `.http` file checked into repo for manual API testing

**Definition of done:** you can register, log in, and search for real NFL players whose data came from an actual external API through your own ingestion pipeline — end to end, no shortcuts.

**Skills exercised:** Spring Security/JWT, first adapter pattern, idempotent upserts, integration testing with Testcontainers.

---

## Phase 2 — Full Data Ingestion Pipeline
**Estimate: 3–4 weeks**

- [ ] `games`, `player_game_stats` tables + ingestion for schedules and weekly stats
- [ ] `injury_reports` table + `InjuryProvider` adapter + scheduled job
- [ ] `betting_lines` table + Odds API adapter + scheduled job
- [ ] `weather_forecasts` table + weather adapter + scheduled job (skip when `games.is_dome = true`)
- [ ] `defense_vs_position_stats` table, computed from ingested stats (your first real aggregation job)
- [ ] `ingestion_runs` table; every job writes a start/finish/status/error row
- [ ] Resilience4j retry + circuit breaker wrapping every external HTTP call
- [ ] Convert manual-trigger jobs to `@Scheduled` with sensible cadences (e.g., injuries daily, odds every few hours, stats post-game)
- [ ] Structured JSON logging with a correlation ID per ingestion run
- [ ] WireMock-based contract tests for each adapter using captured sample vendor payloads

**Definition of done:** all five external data domains flow into Postgres on a schedule, every run is auditable in `ingestion_runs`, and a killed/retried job doesn't create duplicate or corrupt data.

**Skills exercised:** scheduled jobs, retries/circuit breakers, structured logging, contract testing, data normalization.

---

## Phase 3 — Analytics & Scoring Engine
**Estimate: 3–5 weeks — the intellectual core of the project**

- [ ] `recommendations` + `recommendation_factors` tables
- [ ] Design the scoring model on paper first: list factors per position (QB usage factors differ from WR factors), assign initial weights, decide on a 0–100 or z-score-normalized scale
- [ ] Implement factor calculators as pure functions (one class per factor: `MatchupFactorCalculator`, `UsageTrendFactorCalculator`, `RedZoneFactorCalculator`, `VegasImpliedTotalFactorCalculator`, `WeatherFactorCalculator`, `InjuryFactorCalculator`, `StrengthOfScheduleFactorCalculator`) — each one unit-testable in isolation with hand-crafted input data
- [ ] Compose factors into a final score + narrative sentence per factor (e.g., "Ranked 4th in red zone touches over last 3 games")
- [ ] `scoring_version` field populated from a constant/config so future re-weighting doesn't corrupt history
- [ ] `POST /api/recommendations/generate` (batch job, scheduled weekly) that scores every relevant player and writes recommendation + factor rows
- [ ] `GET /api/recommendations/start-sit?week=&position=` — read-only endpoint over already-computed rows
- [ ] `GET /api/players/{id}/trending` (breakout/trending signal, simplest version: recent usage delta)
- [ ] `GET /api/rankings?position=&scoring=ppr` — positional rankings from the same factor engine
- [ ] Heavy unit test coverage on factor calculators (this is where bugs are cheapest to catch and most damaging to ship)
- [ ] Backtest script/notebook: run the engine against last season's completed weeks and sanity-check recommendations against what actually happened

**Definition of done:** hitting a recommendations endpoint returns real players with scores and itemized, human-readable reasoning — and you can point to a unit test proving each factor calculator does what it claims.

**Skills exercised:** recommendation algorithm design, pure-function testability, data engineering, backtesting discipline.

---

## Phase 4 — Caching, Performance, Hardening
**Estimate: 2 weeks**

- [ ] Redis cache-aside for player profiles, rankings, and computed recommendations
- [ ] Cache keys refreshed by the ingestion/scoring jobs themselves (post-write cache population), not lazily on first request
- [ ] Bucket4j rate limiting on public endpoints
- [ ] Global `@ControllerAdvice` + RFC 7807 `problem+json` error contract
- [ ] Input validation (Bean Validation) on all request DTOs, with clear 400 responses
- [ ] Load test critical read endpoints locally (k6) — confirm cache actually reduces DB load under repeated requests
- [ ] Expand integration test suite to cover cache invalidation paths (stale-then-refreshed reads)

**Definition of done:** repeated hits to `/rankings` or `/players/{id}` are served from Redis (verifiable via cache hit logs/metrics), and a malformed request anywhere returns a consistent, documented error shape.

**Skills exercised:** caching strategy, rate limiting, defensive API design, basic performance testing.

---

## Phase 5 — Trade Analyzer
**Estimate: 2–3 weeks**

- [ ] Rest-of-season value model built on top of the Phase 3 scoring engine (reuse factor calculators, add a time-horizon dimension)
- [ ] Positional scarcity adjustment (e.g., an RB scored against RB-specific replacement level, not a global scale)
- [ ] `POST /api/trades/analyze` — accepts two player sets, returns value delta + per-player reasoning reused from `recommendation_factors`
- [ ] Unit tests asserting the trade analyzer composes existing factor calculators rather than duplicating logic
- [ ] Edge case handling: uneven trade sizes (2-for-1), same-position trades, injured/bye-week players in a proposed trade

**Definition of done:** you can submit two lists of players and get back a value comparison with reasoning that traces back to the same factors used in start/sit — proof the scoring engine generalized correctly.

**Skills exercised:** generalizing an existing engine, edge-case-driven testing.

---

## Phase 6 — Observability & Production Readiness
**Estimate: 2 weeks**

- [ ] Spring Boot Actuator enabled (`/health`, `/metrics`, `/info`), locked down appropriately for non-local profiles
- [ ] Micrometer metrics: request latency (p50/p95/p99), cache hit ratio, ingestion job duration/success rate, external API error rate
- [ ] Centralized log shipping (CloudWatch Logs to start)
- [ ] At least one dashboard (CloudWatch or Grafana) showing the metrics above
- [ ] Alarm on: ingestion job failure, elevated 5xx rate, external API circuit breaker open
- [ ] Runbook doc: "if the injury ingestion job fails, here's how to diagnose it" — write one real runbook end to end as a template for others

**Definition of done:** you can kill an external API mid-job, watch the circuit breaker trip in your logs/metrics, and get (or simulate) an alert — without needing to grep raw logs to figure out what happened.

**Skills exercised:** monitoring, alerting, operational documentation.

---

## Phase 7 — AWS Deployment & CI/CD
**Estimate: 2–3 weeks**

- [ ] Dockerfile (multi-stage build, non-root user, slim JRE base image)
- [ ] Terraform (or CDK) modules: VPC, RDS Postgres, ElastiCache Redis, ECS cluster + Fargate service, ALB, ECR, Secrets Manager
- [ ] GitHub Actions: build → test → build+push image to ECR → deploy to ECS on merge to `main`, manual approval gate before prod
- [ ] Move all secrets (DB creds, API keys) into Secrets Manager, injected as env vars at task-definition level
- [ ] Verify scheduled jobs run correctly in the deployed environment (either in-process `@Scheduled` on the always-on service, or migrated to EventBridge-triggered tasks)
- [ ] Smoke test suite run against the deployed environment post-deploy

**Definition of done:** a merge to `main` results in a fully automated deploy to a live AWS environment, with zero manually-typed secrets anywhere in the pipeline.

**Skills exercised:** IaC, container orchestration, secrets management, CI/CD pipelines.

---

## Phase 8 — Frontend
**Estimate: ongoing, lower priority — treat as a thin client**

- [ ] React app: auth screens, player search, player profile, weekly recommendations view with expandable "why" breakdown per factor, trade analyzer form
- [ ] Deliberately minimal client-side state (server is the source of truth); no client-side caching duplication of what Redis already does server-side
- [ ] Deploy as a static site (S3 + CloudFront) separate from the backend deploy pipeline

**Definition of done:** a non-technical user could register, search a player, and understand *why* a recommendation was made without reading your code.

---

## Phase 9+ — Long-Term Vision (post-MVP, pick based on interest)

- [ ] Sleeper API league connection (read-only, no auth needed) → roster-aware personalized recommendations
- [ ] ESPN league connection (harder — undocumented auth) → same
- [ ] Outbox-pattern domain events (injury status change, etc.) → SNS/SQS → push notifications
- [ ] Draft assistant mode: separate scoring weight profile favoring season-long ADP value over weekly matchup value
- [ ] Multi-sport abstraction: promote sport-agnostic concepts (Player/Team/Game) already separated in your schema into a shared module, add a second sport as a new ingestion+scoring pair

---

## Suggested Cadence Summary

| Phase | Weeks (cumulative) | Milestone |
|---|---|---|
| 0 | 1–2 | Local dev environment + CI green |
| 1 | 3–5 | Auth + first real data end-to-end |
| 2 | 6–9 | All 5 data sources ingesting on schedule |
| 3 | 10–14 | Scoring engine producing explainable recommendations |
| 4 | 15–16 | Cached, rate-limited, hardened API |
| 5 | 17–19 | Trade analyzer live |
| 6 | 20–21 | Full observability stack |
| 7 | 22–24 | Deployed on AWS with CI/CD |
| 8 | ongoing | Frontend catches up to backend capability |

**~5–6 months at 10–15 hrs/week** to a genuinely production-shaped MVP (Phases 0–7). That's a realistic, honest estimate — resist compressing it by skipping tests or observability, since those are exactly the parts that make this read as engineering rather than a script.

---

## Weekly Working Rhythm (suggested)

1. Start each week by reviewing the current phase's checklist and picking 2–4 tasks realistic for your available hours.
2. Write the failing test first for anything in the scoring engine or ingestion logic — this is the part of the codebase where regressions are costliest and easiest to introduce.
3. End each week with a short `CHANGELOG.md` entry — this becomes both your portfolio narrative and your own memory of *why* a decision was made when you revisit it in Phase 5 wondering why Phase 3 did something a certain way.
4. Don't start the next phase's tasks until the current phase's "definition of done" is checked off, even if it's tempting — half-finished phases are what make solo projects stall.
