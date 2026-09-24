# Current Work

## Status: Phase 7 first slice (Dockerfile) merged — no active branch

`phase-7/dockerfile` (multi-stage Dockerfile, verified locally end-to-end against real Postgres/Redis) is merged to `main` (PR #18) and the local branch is cleaned up. Everything described below is on `main`.

## What's been completed (Phases 1-5, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2** — games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle, `defense_vs_position_stats`, `weather_forecasts`, `betting_lines`.

**Phase 3** — start/sit recommendation engine (6 factors), player trending endpoint, backtest validation, weight tuning + recent-performance factor (took real predictive correlation from `0.0137` to `0.306`).

**Phase 4** — Redis caching (`GET /recommendations/start-sit` 177ms → 4.6ms avg, `GET /players/{id}` 25.5ms → 3.6ms avg), Bucket4j rate limiting on `/api/auth/**`, Bean Validation on previously-unconstrained request params.

**Phase 5, first slice** — trade analyzer (`POST /api/trades/analyze`): rest-of-season value comparison reusing three of the six factor calculators plus a positional replacement-level adjustment. ~3.3s per request (known, deliberately-accepted N+1 cost), not batched yet.

**Phase 6, first slice** — Micrometer instrumentation for ingestion job duration/success (`ingestion.run.duration`, tagged by source/outcome). Request latency, cache hit ratio, and external API error rate turned out to already be auto-instrumented for free — confirmed live rather than assumed. AWS-dependent rest of Phase 6 (CloudWatch shipping, dashboards, alarms) waits on Phase 7's infrastructure.

Full writeups for all of the above in `CLAUDE.md`.

## Phase 7, first slice: Dockerfile (PR #18, merged)

- Multi-stage `Dockerfile` — stage 1 builds the jar by running Gradle *inside* the container (also sidesteps this dev machine's known Gradle loopback issue, since a Linux container doesn't have it); stage 2 is a separate slim `eclipse-temurin:21-jre-alpine`, non-root user, `HEALTHCHECK` against `/actuator/health`.
- Tests deliberately skipped in the image build (`-x test`) — no Testcontainers/real Postgres/Redis available inside a `docker build` context; CI already runs the real suite on every push.
- **Verified locally end-to-end**: ran the built image attached to the existing `docker-compose` network against the real Postgres/Redis containers via Docker's service-name DNS, confirmed Flyway validated all 14 migrations, `/actuator/health` returned `UP`, Docker's own `HEALTHCHECK` reported `healthy`, and a real login + authenticated player search round-tripped correctly. Image: 416MB.

Full design rationale in `CLAUDE.md`'s "Docker" section.

**Not started yet**: everything else in Phase 7 — Terraform/CDK (VPC, RDS, ElastiCache, ECS/Fargate, ALB, ECR), the GitHub Actions build-push-deploy pipeline, Secrets Manager. This is real, billable AWS infrastructure, so it's waiting on two things from the user before any code gets written:

1. **Local tooling**: AWS CLI installed + configured (`aws configure`, using an IAM user/role, not root credentials), Terraform installed, a billing alert set up in AWS Budgets.
2. **Scope decisions**: which AWS region, and budget posture (minimal-cost single-AZ/no-NAT-gateway setup vs. a more "real" HA configuration) — user confirmed they have an AWS account, these two specifics are still outstanding.

I do not have AWS CLI/Terraform installed in this session's environment, and no AWS credentials are configured here — confirmed by checking, not assumed. Even if they were, I shouldn't be the one running `terraform apply`/`aws` commands that spend the user's money without them directly watching it happen — matches how API keys were handled earlier (user sets up credentials themselves, never pastes them into chat). My role for the rest of Phase 7 is writing the Terraform/CI code; the user runs `terraform plan`/`apply` themselves.

## What remains (lower priority, not blocking)

- Trade analyzer performance (N+1 query pattern in `computeReplacementLevels`, ~3.3s per request) — not urgent, occasional endpoint
- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one)
- Backtest performance (N+1 query pattern inside `gatherFactors`, ~18 min for a full season) — not urgent, occasional endpoint
- `MatchupFactorCalculator`'s long-run uniform averaging and the weak `USAGE` factor remain real, un-investigated hypotheses if further model improvement is wanted later

## Recommended next steps

1. **Continue Phase 7** — once the user confirms AWS CLI/Terraform are installed and configured, and picks a region + budget posture, write the Terraform modules (VPC, RDS, ElastiCache, ECS, ALB, ECR, Secrets Manager) for the user to `plan`/`apply` themselves, then the GitHub Actions deploy pipeline.
2. **Batch the trade analyzer's replacement-level computation** if the 3.3s cost turns out to matter in practice.
3. **Phase 8** — frontend, lower priority per the dev plan, likely follows Phase 7.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, `phase-3/start-sit-scoring-engine`, `phase-3/player-trending-endpoint`, and `phase-3/backtest-validation` still exist on origin from prior slices (deferred cleanup, unchanged). `phase-7/dockerfile` has been deleted both locally and remotely.

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas").
