# Current Work

## Status: Full AWS infrastructure built, verified live end-to-end, then torn down — no active branch

All of Phase 7's Terraform is merged to `main` (PRs #18-21) and proven to work against a real AWS account: the app was genuinely reachable on the public internet, `/actuator/health` returned `UP` (which, since Spring Boot's health check aggregates its DB/Redis indicators, already confirmed RDS and ElastiCache connectivity), and a real user registration through the live URL confirmed the JWT secret wiring too. Everything was then `terraform destroy`'d (42 resources) to stop billing — **nothing is currently running in AWS**. The infrastructure code is real, complete, and already proven; running it again next time is just `terraform apply`.

## What's been completed (Phases 1-5, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2** — games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle, `defense_vs_position_stats`, `weather_forecasts`, `betting_lines`.

**Phase 3** — start/sit recommendation engine (6 factors), player trending endpoint, backtest validation, weight tuning + recent-performance factor (took real predictive correlation from `0.0137` to `0.306`).

**Phase 4** — Redis caching (`GET /recommendations/start-sit` 177ms → 4.6ms avg, `GET /players/{id}` 25.5ms → 3.6ms avg), Bucket4j rate limiting on `/api/auth/**`, Bean Validation on previously-unconstrained request params.

**Phase 5, first slice** — trade analyzer (`POST /api/trades/analyze`): rest-of-season value comparison reusing three of the six factor calculators plus a positional replacement-level adjustment. ~3.3s per request (known, deliberately-accepted N+1 cost), not batched yet.

**Phase 6, first slice** — Micrometer instrumentation for ingestion job duration/success (`ingestion.run.duration`, tagged by source/outcome). Request latency, cache hit ratio, and external API error rate turned out to already be auto-instrumented for free — confirmed live rather than assumed. AWS-dependent rest of Phase 6 (CloudWatch shipping, dashboards, alarms) waits on Phase 7's infrastructure.

Full writeups for all of the above in `CLAUDE.md`.

## Phase 7 infrastructure (PRs #18-21, all merged)

Setup done first: AWS CLI + Terraform installed locally, an IAM user (`fantasyiq-terraform`, `AdministratorAccess` for now) created for Terraform to use, a billing alert set up in AWS Budgets. Account is on AWS's credit-based Free Plan (6 months or until credits run out), which is *why* the build-and-destroy rhythm matters here — RDS/ElastiCache/ALB running continuously would burn through it in 2-3 months instead of 6. Region: `us-east-1`. Budget posture: minimal-cost (single-AZ, no NAT gateway, smallest reasonable instance sizes).

**PR #18 — Dockerfile.** Multi-stage build: stage 1 builds the jar by running Gradle *inside* the container (also sidesteps this dev machine's known Gradle loopback issue); stage 2 is a slim `eclipse-temurin:21-jre-alpine`, non-root user, `HEALTHCHECK` against `/actuator/health`. Tests skipped in the image build (`-x test`) — no Testcontainers available in a `docker build` context; CI runs the real suite. Verified locally against real `docker-compose` Postgres/Redis before ever touching AWS.

**PR #19 — Terraform network layer.** VPC, internet gateway, 2 public + 2 private subnets across 2 AZs, a public route table. No NAT gateway (~$32/month avoided) — the app runs in a public subnet with its own public IP instead. Free to run.

**PR #20 — Security groups + ECR.** Four security groups chained so each layer only accepts traffic from the one in front of it (`internet → alb (80) → app (8080) → db (5432)/redis (6379)`). ECR repository with immutable tags and a 10-image retention policy. Both free; the built image was pushed to ECR by hand to confirm the mechanics (hit and fixed a Docker Desktop credential-helper bug along the way — a Docker Desktop restart resolved it).

**PR #21 — Database, cache, and compute layer.** The first resources that cost money while they exist:
- RDS Postgres (`db.t4g.micro`, single-AZ, no automated backups) and a single-node ElastiCache Redis, both private, reachable only from the app. RDS's master password is never set in code at all — `manage_master_user_password` has AWS generate and own it directly in Secrets Manager.
- A real Terraform-generated `JWT_SECRET` (safe to generate in code, unlike a real credential) plus empty placeholder secrets for `OPENWEATHER_API_KEY`/`ODDS_API_KEY` — the user sets the real values via `aws secretsmanager put-secret-value`, run by hand, never through this code or chat.
- An ALB (HTTP only — no domain/cert yet), an ECS Fargate service (0.5 vCPU/1GB, not Fargate's minimum, to avoid a likely JVM OOM crash loop), a narrowly-scoped IAM execution role, and a CloudWatch log group.
- The task reuses the exact `SPRING_DATASOURCE_URL`/`SPRING_DATA_REDIS_HOST` env-var-override pattern already verified locally in PR #18 — same mechanism, now pointed at RDS/ElastiCache.

Full design rationale for all four PRs in `CLAUDE.md`'s "Docker" and infrastructure sections.

**Verified live, then torn down.** `terraform apply` (RDS took ~5 min, ElastiCache ~3 min to provision — real servers, not configuration, which is also why they're the first things in this project to cost money while they exist). Confirmed `/actuator/health` returns `UP` through the load balancer's public DNS name, and a real `/api/auth/register` call through that same URL succeeded. Then `terraform destroy` — 42 resources removed, back to $0. One real Terraform/AWS lesson from the teardown: the internet gateway took ~7 minutes to destroy, gated by the ECS task's network interface still being attached inside the VPC — a dependency AWS enforces that Terraform's own plan graph didn't know about, since nothing in the code links them directly.

**Not started yet**: the GitHub Actions build-push-deploy pipeline (OIDC role assumption planned, no long-lived AWS keys in GitHub), and the CloudWatch-dashboard/alarms half of Phase 6 that depends on this infrastructure actually running continuously.

## What remains (lower priority, not blocking)

- Trade analyzer performance (N+1 query pattern in `computeReplacementLevels`, ~3.3s per request) — not urgent, occasional endpoint
- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one)
- Backtest performance (N+1 query pattern inside `gatherFactors`, ~18 min for a full season) — not urgent, occasional endpoint
- `MatchupFactorCalculator`'s long-run uniform averaging and the weak `USAGE` factor remain real, un-investigated hypotheses if further model improvement is wanted later

## Recommended next steps

1. **The GitHub Actions deploy pipeline** — the last real piece of Phase 7. On a `git push`: build the image, push to ECR tagged with the commit SHA (replacing today's manual `manual-1` tag), then update the ECS service. Needs an OIDC trust relationship between GitHub and AWS set up first (no long-lived AWS access keys stored in GitHub secrets).
2. **Re-`terraform apply` before testing the pipeline** — infrastructure is fully torn down right now; the pipeline needs a live ECS service to actually deploy to.
3. **Batch the trade analyzer's replacement-level computation** if the 3.3s cost turns out to matter in practice.
4. **Phase 8** — frontend, lower priority per the dev plan, likely follows Phase 7.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, `phase-3/start-sit-scoring-engine`, `phase-3/player-trending-endpoint`, and `phase-3/backtest-validation` still exist on origin from prior slices (deferred cleanup, unchanged). All four Phase 7 branches (`phase-7/dockerfile`, `phase-7/terraform-network`, `phase-7/terraform-security-ecr`, `phase-7/terraform-app-live`) have been deleted both locally and remotely.

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified. **No AWS resources are currently running** — this is expected, not a problem; it's the intended state between sessions given the credit-limited account. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas").
