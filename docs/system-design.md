# FantasyIQ — System Design Document

**A fantasy football analytics & recommendation platform**
Version 1.0 — Solo-developer production build

---

## 0. Design Philosophy

Before the details, four principles drive every decision below:

1. **Ingestion and analysis are separable from serving.** Data pulled from external APIs is messy, rate-limited, and inconsistent. It gets normalized into your own schema on a schedule, independent of what users are doing. The API layer never talks to third-party APIs directly on a user's request thread.
2. **Every recommendation is a materialized explanation, not a live LLM guess.** The "why" (matchup, usage, red zone share, etc.) is computed by deterministic scoring functions over normalized data and stored alongside the recommendation. This is what makes the system explainable, testable, and cheap to serve.
3. **Solo-developer scope discipline.** A real engineering team would split ingestion, scoring, and serving into separate deployable services. You will build them as separate **modules within a modular monolith**, with clean boundaries so they *could* be split later. This gets you 90% of the architectural learning with 30% of the operational overhead.
4. **Everything is observable.** If a scheduled job silently fails to pull injury data, your recommendations quietly go stale and you won't know unless you build monitoring in from week one.

---

## 1. High-Level Architecture

```
                                   ┌─────────────────────────┐
                                   │   External Data Sources │
                                   │  (Stats/Odds/Weather/    │
                                   │   Injuries/Schedules)    │
                                   └────────────┬─────────────┘
                                                │  scheduled pulls (Spring @Scheduled / Quartz)
                                                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                          FantasyIQ Backend (Spring Boot, modular monolith) │
│                                                                             │
│  ┌───────────────┐   ┌────────────────┐   ┌───────────────────────────┐  │
│  │ Ingestion      │──▶│ Normalization/  │──▶│ PostgreSQL (source of     │  │
│  │ Module         │   │ ETL Module      │   │ truth: players, stats,    │  │
│  │ (adapters per  │   │ (maps vendor    │   │ injuries, games, odds)    │  │
│  │ external API)  │   │ schemas → domain│   │                           │  │
│  └───────────────┘   │ model)          │   └────────────┬──────────────┘  │
│                       └────────────────┘                │                 │
│                                                          ▼                 │
│                                          ┌───────────────────────────┐    │
│                                          │ Analytics / Scoring Engine │    │
│                                          │ (start/sit, waiver, trade, │    │
│                                          │  breakout, rankings)       │    │
│                                          │ writes recommendation +    │    │
│                                          │ explanation rows           │    │
│                                          └────────────┬──────────────┘    │
│                                                        │                   │
│                       ┌────────────────────────────────┴───────┐          │
│                       ▼                                        ▼          │
│              ┌─────────────────┐                      ┌────────────────┐ │
│              │ Redis Cache      │◀────read-through────▶│ REST API Layer │ │
│              │ (hot player      │                      │ (Spring MVC,   │ │
│              │ profiles,        │                      │ Spring Security│ │
│              │ rankings, weekly │                      │ JWT auth)      │ │
│              │ recs)            │                      └───────┬────────┘ │
│              └─────────────────┘                                │          │
└────────────────────────────────────────────────────────────────┼──────────┘
                                                                   ▼
                                                          ┌──────────────────┐
                                                          │  React Frontend  │
                                                          │  (thin client)   │
                                                          └──────────────────┘
```

**Why a modular monolith instead of microservices?** Microservices solve organizational scaling problems (independent teams, independent deploys) that a solo developer doesn't have. What you actually want to practice — clean module boundaries, async processing, caching, retries — is fully achievable inside one deployable Spring Boot app organized into packages-by-feature with enforced dependency rules (e.g., via ArchUnit tests). You get a system that *could* be split into `ingestion-service`, `analytics-service`, and `api-service` later without a rewrite, because the boundaries are already clean.

---

## 2. Development Phases

Scope is sequenced so that every phase ends with something demonstrably working — never a half-finished layer.

### Phase 0 — Foundations (1–2 weeks)
- Repo setup, Java 21 + Spring Boot 3.x project skeleton, Gradle/Maven build.
- Docker Compose with Postgres + Redis for local dev.
- CI pipeline (GitHub Actions): build, test, lint on every PR.
- Base package structure enforcing module boundaries (see §4).
- Flyway or Liquibase wired up for migrations from day one — never hand-edit schema.

### Phase 1 — Core Domain & Auth (2–3 weeks)
- User registration/login, Spring Security + JWT (access + refresh tokens).
- Player domain model + Postgres schema for players, teams, positions.
- One external stats API integrated end-to-end (ingestion → normalization → DB) to prove the pipeline works before adding more sources.
- Player search and player profile REST endpoints.
- Unit tests for services, integration tests (Testcontainers + Postgres) for repositories.

### Phase 2 — Data Ingestion Expansion (3–4 weeks)
- Add remaining sources: injuries, schedules/matchups, betting lines, weather.
- Build the **adapter pattern**: one interface per data domain (`StatsProvider`, `InjuryProvider`, `OddsProvider`, `WeatherProvider`), one implementation per vendor, so vendors are swappable.
- Scheduled jobs (Spring `@Scheduled` to start, graduate to Quartz if you need persistence/clustering) with retry + backoff (Resilience4j) and circuit breakers for flaky third-party APIs.
- Job run history table (audit log of every ingestion run: status, rows processed, errors) — your first real "ops" table.
- Structured logging (JSON logs) + correlation IDs across a single ingestion run.

### Phase 3 — Analytics & Scoring Engine (3–5 weeks)
- Define the scoring model: weighted factors (matchup, usage, target share, red zone share, Vegas implied total, weather, SOS, injury status) per position, producing a numeric score + a structured explanation object.
- Start/sit recommendation endpoint.
- Waiver wire recommendation endpoint (trending + opportunity-based).
- Player rankings endpoint (position-ranked, PPR/half-PPR/standard configurable).
- Every recommendation row stores its explanation as structured data (not just a text blob) so the frontend can render "why" as itemized factors.
- This is the intellectual core of the project — expect to iterate on weighting logic the most here.

### Phase 4 — Caching, Performance, Hardening (2 weeks)
- Redis caching for: player profiles, weekly rankings, computed recommendations (all invalidated/refreshed by ingestion jobs, not by request traffic).
- Cache-aside pattern with explicit TTLs, cache-stampede protection for popular players.
- Rate limiting on public endpoints (Bucket4j).
- Global exception handling (`@ControllerAdvice`), consistent error response contract (RFC 7807 problem+json).
- Expanded integration test suite; contract tests for external adapters using WireMock so tests don't hit real vendor APIs.

### Phase 5 — Trade Analyzer (2–3 weeks)
- Trade value model built on top of the scoring engine (rest-of-season value, positional scarcity adjustment).
- "Analyze this trade" endpoint: takes two proposed player sets, returns value delta + reasoning.
- This phase reuses Phase 3's engine rather than inventing a new one — a good forcing function to make sure the scoring engine is generalized, not start/sit-specific.

### Phase 6 — Observability & Production Readiness (2 weeks)
- Spring Boot Actuator + Micrometer metrics.
- Centralized logging (CloudWatch Logs or self-hosted Loki/Grafana).
- Dashboards: ingestion job health, API latency (p50/p95/p99), cache hit ratio, external API error rates.
- Alerting on ingestion job failure and elevated 5xx rate.
- Load testing (k6 or Gatling) against critical endpoints.

### Phase 7 — AWS Deployment & CI/CD (2–3 weeks)
- Containerize app; push to ECR.
- Deploy on ECS Fargate (see §6 for the full reasoning).
- RDS Postgres, ElastiCache Redis, Secrets Manager for credentials, ALB in front of ECS.
- GitHub Actions: build → test → build image → push → deploy on merge to main, with a manual approval gate for production.
- Infrastructure as code with Terraform (or AWS CDK if you prefer Java-native IaC) — this alone is a strong resume line.

### Phase 8 — Frontend Polish (ongoing, lower priority)
- React app consuming the REST API: dashboard, player search, recommendation views with explanation breakdowns, trade analyzer UI.
- Deliberately kept simple (server-driven, minimal client state) since the point of the project is the backend.

### Phase 9+ — Long-Term Vision (post-MVP)
- Sleeper/ESPN league OAuth-style connection, roster sync, personalized recommendations scoped to a user's actual roster.
- Push notifications (SNS + mobile/web push) for injury/news events, driven off the ingestion pipeline's change-detection.
- Draft assistant mode (different scoring weights: season-long ADP-aware value vs. weekly matchup value).
- Multi-sport abstraction (the domain model already separates "sport-agnostic" concepts like Player/Team/Game from "NFL-specific" scoring factors, so this is a schema extension, not a rewrite, if you design §3 correctly now).

---

## 3. Database Schema (PostgreSQL)

Design principles: normalize the raw domain (players, games, stats) but keep computed recommendations as their own denormalized, explainable rows — don't try to derive "why" on the fly at read time.

### 3.1 Identity & Access
```sql
users (
  id UUID PK,
  email VARCHAR UNIQUE NOT NULL,
  password_hash VARCHAR NOT NULL,
  display_name VARCHAR,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
)

refresh_tokens (
  id UUID PK,
  user_id UUID FK -> users,
  token_hash VARCHAR,
  expires_at TIMESTAMPTZ,
  revoked BOOLEAN DEFAULT FALSE
)
```

### 3.2 Core Football Domain
```sql
teams (
  id SERIAL PK,
  external_ref VARCHAR,           -- vendor's team id, for mapping
  abbreviation VARCHAR(4) UNIQUE, -- 'KC', 'SF'
  name VARCHAR,
  conference VARCHAR,
  division VARCHAR
)

players (
  id UUID PK,
  external_ref VARCHAR,           -- vendor id for reconciliation
  full_name VARCHAR NOT NULL,
  position VARCHAR(4) NOT NULL,   -- QB/RB/WR/TE/K/DST
  current_team_id INT FK -> teams,
  jersey_number INT,
  status VARCHAR,                 -- ACTIVE/INJURED_RESERVE/OUT/etc (denormalized latest status)
  birth_date DATE,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
)
CREATE INDEX idx_players_name_trgm ON players USING GIN (full_name gin_trgm_ops); -- fast fuzzy search

games (
  id UUID PK,
  external_ref VARCHAR,
  season INT,
  week INT,
  home_team_id INT FK -> teams,
  away_team_id INT FK -> teams,
  kickoff TIMESTAMPTZ,
  venue VARCHAR,
  is_dome BOOLEAN,
  status VARCHAR                   -- SCHEDULED/IN_PROGRESS/FINAL
)

player_game_stats (
  -- Scoped to QB/RB/WR/TE. K/DST use team-level ESPN endpoints, not the
  -- per-athlete gamelog this table is populated from -- a separate future task.
  id BIGSERIAL PK,
  player_id UUID FK -> players,
  game_id UUID FK -> games,
  snaps INT,                      -- NOT populated: confirmed absent from ESPN's free-tier gamelog data
  snap_pct NUMERIC(5,2),          -- NOT populated, same reason
  targets INT,
  receptions INT,
  rec_yards INT,
  rush_attempts INT,
  rush_yards INT,
  red_zone_touches INT,           -- NOT populated: confirmed absent from ESPN's free-tier gamelog data
  passing_attempts INT,           -- added: original sketch only anticipated skill-position stats
  passing_completions INT,
  passing_yards INT,
  passing_touchdowns INT,
  interceptions INT,
  touchdowns INT,                 -- total across passing + rushing + receiving
  fantasy_points_ppr NUMERIC(6,2),      -- computed by us; ESPN doesn't provide fantasy points
  fantasy_points_standard NUMERIC(6,2), -- computed by us, standard scoring rules
  UNIQUE (player_id, game_id)
)
```

### 3.3 Context Data (feeds the scoring engine)
```sql
injury_reports (
  id BIGSERIAL PK,
  player_id UUID FK -> players,
  report_date DATE,
  status VARCHAR,       -- QUESTIONABLE/DOUBTFUL/OUT/IR
  body_part VARCHAR,
  practice_participation VARCHAR,  -- DNP/LIMITED/FULL
  source VARCHAR,
  fetched_at TIMESTAMPTZ
)

betting_lines (
  id BIGSERIAL PK,
  game_id UUID FK -> games,
  team_id INT FK -> teams,
  implied_team_total NUMERIC(5,2),
  spread NUMERIC(5,2),
  over_under NUMERIC(5,2),
  source VARCHAR,
  fetched_at TIMESTAMPTZ
)

weather_forecasts (
  id BIGSERIAL PK,
  game_id UUID FK -> games,
  temperature_f INT,
  wind_mph INT,
  precipitation_pct INT,
  conditions VARCHAR,
  fetched_at TIMESTAMPTZ
)

defense_vs_position_stats (
  id BIGSERIAL PK,
  team_id INT FK -> teams,     -- defense being measured
  season INT,
  week INT,
  position VARCHAR(4),
  fantasy_points_allowed NUMERIC(6,2),
  rank INT                       -- 1 = toughest matchup, 32 = easiest
)
```

### 3.4 Recommendations & Explanations (the differentiator)
```sql
recommendations (
  id UUID PK,
  player_id UUID FK -> players,
  week INT,
  season INT,
  type VARCHAR,           -- START_SIT / WAIVER / TRADE / BREAKOUT / RANKING
  score NUMERIC(6,3),
  confidence VARCHAR,     -- HIGH/MEDIUM/LOW
  generated_at TIMESTAMPTZ,
  scoring_version VARCHAR -- which version of the scoring algorithm produced this
)

recommendation_factors (
  id BIGSERIAL PK,
  recommendation_id UUID FK -> recommendations,
  factor_type VARCHAR,    -- MATCHUP/USAGE/SNAP_PCT/TARGET_SHARE/RED_ZONE/VEGAS/WEATHER/INJURY/SOS/TREND
  factor_value NUMERIC(10,4),
  factor_weight NUMERIC(5,4),
  contribution NUMERIC(8,4),   -- factor_value * weight, pre-computed
  narrative TEXT                -- human-readable sentence, e.g. "Faces the #29 ranked pass defense"
)
```
This factor table is what lets the frontend render "Start Travis Kelce — 3 supporting factors, 1 concern" as a structured breakdown instead of a hardcoded string, and it's what lets you unit test the scoring engine (assert on factor rows, not just a final number).

### 3.5 User-Facing / Roster Data (Phase 9+)
```sql
fantasy_leagues (
  id UUID PK, user_id FK -> users, provider VARCHAR, external_league_id VARCHAR, league_name VARCHAR
)
roster_slots (
  id UUID PK, league_id FK -> fantasy_leagues, player_id FK -> players, slot_type VARCHAR
)
```

### 3.6 Operational Tables
```sql
ingestion_runs (
  id UUID PK,
  source VARCHAR,          -- STATS_PROVIDER / INJURY_PROVIDER / ODDS_PROVIDER / WEATHER_PROVIDER
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  status VARCHAR,          -- SUCCESS/PARTIAL/FAILED
  records_processed INT,
  error_message TEXT
)
```

**Indexing notes:** composite indexes on `(week, season, position)` for rankings queries; `(player_id, week, season)` on stats and recommendations; partial indexes on `injury_reports` where `status != 'ACTIVE'` since that's the hot query path for the scoring engine.

---

## 4. Backend Architecture (Spring Boot)

### 4.1 Package structure (feature-first, not layer-first, at the top level)
```
com.fantasyiq
├── ingestion/
│   ├── stats/           (adapter interface + vendor impls)
│   ├── injuries/
│   ├── odds/
│   ├── weather/
│   └── scheduler/       (job orchestration, ingestion_runs bookkeeping)
├── domain/
│   ├── player/          (entity, repository, service)
│   ├── team/
│   ├── game/
│   └── stats/
├── analytics/
│   ├── scoring/         (factor calculators, weighting config)
│   ├── startsit/
│   ├── waiver/
│   ├── trade/
│   └── rankings/
├── api/
│   ├── controller/      (thin — validate, delegate, map DTOs)
│   ├── dto/
│   └── exception/       (@ControllerAdvice, error contract)
├── auth/
│   ├── security config, JWT filter, user service
├── cache/
│   └── Redis config, cache key strategy
├── common/
│   └── shared value objects, clock/config abstractions
└── config/
```
Within each feature package, use a conventional layered split (`controller/service/repository/entity`) — layering *within* a feature, not *across* the whole app, is what keeps the modular monolith from becoming a big ball of mud. Enforce boundaries with ArchUnit tests (e.g., "ingestion must never depend on api", "analytics must not import controller classes").

### 4.2 Key patterns to practice deliberately
- **Adapter/Strategy pattern** for external providers (`StatsProvider` interface, `SportsDataIoStatsProvider` implementation) — lets you swap or add vendors without touching ingestion orchestration.
- **Retry + circuit breaker** (Resilience4j) around every external call; exponential backoff with jitter; dead-letter the failed batch into `ingestion_runs` rather than silently dropping it.
- **Idempotent ingestion**: upsert by `external_ref`, never assume "insert" is safe, since jobs may re-run after a partial failure.
- **CQRS-lite**: writes go through domain services; reads for the "hot path" (rankings, weekly recs) go through a read-optimized query layer backed by Redis, decoupled from the write model.
- **Cache-aside with explicit invalidation**: cache keys are refreshed by the ingestion/scoring jobs (`players:{id}`, `rankings:{season}:{week}:{position}:{scoring}`), not by request-driven population, since staleness tolerance here is "until next data refresh," not "until TTL expires."
- **Outbox-style event log** for future notifications: when an injury status changes, write a domain event row; a separate consumer (later, SNS) reads it. This is what makes Phase 9 push notifications a natural extension instead of a bolt-on.

### 4.3 Testing strategy
- **Unit tests**: scoring engine factor calculators (pure functions — easiest and most valuable to test exhaustively), DTO mapping, validation.
- **Integration tests**: Testcontainers spinning up real Postgres + Redis; verify repository queries and cache behavior.
- **Contract tests**: WireMock stand-ins for each external vendor, asserting your adapter parses real captured vendor payloads correctly, and that retry/circuit-breaker logic triggers on 5xx/timeouts.
- **API tests**: Spring `MockMvc` / RestAssured against the controller layer including security filters.

---

## 5. External Data Sources

No single free API covers all of stats + injuries + odds + weather, so plan for 3–4 integrations:

| Domain | Recommended option(s) | Notes |
|---|---|---|
| Player stats, rosters, schedules | **SportsDataIO** (paid, generous free/dev tier) or **nfl-data-py**-style public datasets (e.g. `nflverse`/`nflfastR` data on GitHub, free) | SportsDataIO gives you a clean commercial-grade REST API — good for practicing production integration patterns. `nflverse` data is free and excellent for historical/backtesting data but is batch/CSV-based, not a live REST API — good as a secondary/backfill source. |
| Injuries | SportsDataIO injury endpoint, or scraping-free options like **ESPN's public (undocumented) API** | Treat undocumented APIs as unstable — wrap them behind the adapter interface so you can swap without touching the rest of the system. |
| Betting lines / Vegas totals | **The Odds API** (has a free tier) | Clean, purpose-built for exactly this. |
| Weather | **OpenWeatherMap** or **Tomorrow.io** (both have free tiers) | Only relevant for outdoor stadiums — use `games.is_dome` to skip the call entirely, saving quota. |
| Trending/ownership signals | Sleeper's public read-only API (no auth required for league/player data) | Good free source for "trending adds" style signals later. |

Practical note: start MVP with **one paid-tier-optional provider for stats** (SportsDataIO trial or nflverse historical data) plus **The Odds API** and **OpenWeatherMap**, both of which have workable free tiers. Add ESPN/Sleeper integrations in Phase 9 when you build league connection.

---

## 6. AWS Architecture

| Concern | Service | Why |
|---|---|---|
| Compute | **ECS Fargate** | Runs your Spring Boot container without managing EC2 instances; simpler than EKS for a solo dev while still teaching you real container orchestration, task definitions, and service scaling. |
| Database | **RDS for PostgreSQL** | Managed backups, automated failover option, parameter groups — teaches you real DB ops without hand-rolling replication. |
| Cache | **ElastiCache for Redis** | Matches your local Redis setup; managed failover if you enable Multi-AZ later. |
| Container registry | **ECR** | Standard pairing with ECS; integrates cleanly with GitHub Actions. |
| Load balancing / TLS | **Application Load Balancer** + **ACM** | Path-based routing if you ever split services; free managed certs. |
| Secrets | **Secrets Manager** (or Parameter Store to save cost) | Never bake API keys into images or env files in the repo. |
| Async/events (Phase 9+) | **SNS** + **SQS** | Injury/news push notifications; SQS also useful as a durable queue between ingestion and scoring if you want to decouple them further. |
| Scheduled jobs | **EventBridge Scheduler** triggering an ECS task, *or* Spring's own scheduler inside the always-on service | Start with in-process Spring `@Scheduled` (simpler); migrate specific jobs to EventBridge + a separate Fargate task if you want to demonstrate serverless-triggered batch processing. |
| Logs & metrics | **CloudWatch Logs**, **CloudWatch Metrics/Alarms**, optionally **Managed Grafana** | Central place for the observability work in Phase 6. |
| IaC | **Terraform** (or AWS CDK with Java, which lets you write infra in the same language as the app) | Reproducible environments; a strong signal of production maturity. |
| CI/CD | **GitHub Actions** → ECR → ECS deploy | Free for personal repos, integrates well with everything above. |

**Cost control for a solo dev:** use `db.t4g.micro`/`cache.t4g.micro` for RDS/ElastiCache, Fargate Spot for non-critical scheduled ingestion tasks, and turn off non-prod environments when not actively developing. This is itself worth documenting in your README as a deliberate cost-engineering decision.

---

## 7. Why These Choices (Summary Rationale)

- **Modular monolith over microservices**: matches solo-dev reality while preserving the option to split later; avoids distributed-systems overhead (service discovery, distributed tracing, network partitions) that would dominate your time budget without teaching backend fundamentals.
- **Postgres over NoSQL**: your domain is deeply relational (players ↔ teams ↔ games ↔ stats ↔ recommendations) and you want strong consistency for recommendation correctness — a fantasy manager needs to trust the numbers.
- **Redis for read-path caching only, not as source of truth**: keeps cache invalidation simple (refresh-driven, not TTL-guessing) and keeps Postgres as the single place recommendations can be audited/debugged from.
- **Recommendation + factor tables instead of live computation**: makes the system auditable (you can always answer "why did we say this two weeks ago") and makes the scoring engine unit-testable in isolation from the API layer.
- **Adapter pattern for every external API**: vendors change, deprecate endpoints, or rate-limit you; isolating vendor-specific logic behind interfaces is the single highest-leverage decision for long-term maintainability.
- **ECS Fargate over Lambda for the main app**: your workload is a long-running stateful API with scheduled jobs and connection pooling to Postgres/Redis — a poor fit for Lambda's execution model. Lambda becomes a good fit later for specific short-lived tasks (e.g., a single ingestion job) if you want to show serverless skills too.
- **Explicit `scoring_version` on recommendations**: lets you change your weighting algorithm over time without corrupting historical analysis — a real production concern (model/algorithm versioning) most portfolio projects never touch.

---

## 8. Realistic Solo-Developer Scope Guardrails

- Ship Phases 0–4 (auth, one full ingestion pipeline, scoring engine, caching) before touching AWS at all — a working local system beats a half-deployed one.
- Cap MVP to **one primary stats provider**; add redundant/secondary sources only after the core pipeline and scoring engine are solid.
- Resist building a custom job scheduler or message queue from scratch — Spring's `@Scheduled` + Resilience4j is enough until you have a concrete reason (e.g., wanting to demonstrate SQS-based decoupling) to add more infrastructure.
- Treat the React frontend as a thin consumer of a well-designed API, not a project in itself — this keeps your time budget weighted toward backend engineering, which is the stated goal.
- Write the README as if onboarding a new engineer: architecture diagram, "why" for each major decision, and a "how to run locally with Docker Compose in under 5 minutes" section. This is what makes the project read as production software rather than a tutorial project.

---

## Next Steps

A natural next artifact would be the **entity-relationship diagram** rendered visually, or a **detailed API contract** (OpenAPI spec) for the Phase 1–3 endpoints. Let me know which you'd like to tackle first, or if you want to dig deeper into the scoring engine's actual weighting formulas.
