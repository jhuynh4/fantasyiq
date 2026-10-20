# Current Work

## Status: backend complete through Phase 7 plus a hardening pass (PRs #27-#32) — Phase 8 (frontend) not started

Phases 1-7 are merged to `main`. After a full backend/cloud review, a hardening pass (PRs #27-#32, below) closed the gaps the review found. **Nothing is currently running in AWS** — everything was `terraform destroy`'d to stop billing (the account is on a credit-limited plan, hence the apply → verify → destroy rhythm). The infrastructure and pipeline code were proven live earlier; **PR #29's infra changes have never run against real AWS** (see "Pending: first apply of PR #29").

The next real step is the frontend. It needs a few decisions first (see "Recommended next steps").

## What's been completed (Phases 1-7, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2** — games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle, `defense_vs_position_stats`, `weather_forecasts`, `betting_lines`.

**Phase 3** — start/sit recommendation engine (6 factors), player trending endpoint, backtest validation, weight tuning + recent-performance factor (took real predictive correlation from `0.0137` to `0.306`).

**Phase 4** — Redis caching, Bucket4j rate limiting on `/api/auth/**`, Bean Validation on previously-unconstrained request params.

**Phase 5, first slice** — trade analyzer (`POST /api/trades/analyze`): rest-of-season value comparison reusing three of the six factor calculators plus a positional replacement-level adjustment.

**Phase 6** — Micrometer metrics (`ingestion.run.duration`; request latency, cache hit ratio and circuit-breaker state were auto-instrumented), plus a CloudWatch dashboard, 4 alarms via SNS email, and a runbook (`docs/runbooks/injury-ingestion-failure.md`).

**Phase 7** — multi-stage Dockerfile, Terraform for the whole AWS stack (VPC, ALB, ECS Fargate, RDS, ElastiCache, ECR, Secrets Manager, IAM), and a GitHub Actions deploy pipeline authenticating via OIDC (no stored AWS keys). Found and fixed live: Secrets Manager's 30-day recovery window vs. destroy-and-recreate, GitHub's "immutable subject claims" token format, and an ECS crash loop from a missing `health_check_grace_period_seconds`.

Full writeups of all of the above in `CLAUDE.md`.

## Backend hardening pass (PRs #27-#32, all merged)

Found by reading the code end to end after Phase 7. Each was its own PR; details and the evidence behind each are in `CLAUDE.md`'s "Known gaps" section.

- **#27 — 401 and `expiresIn`.** Missing/malformed/expired access tokens now return 401 (`UnauthorizedEntryPoint`, with `WWW-Authenticate: Bearer`) instead of Spring Security's default 403, and every auth response includes `expiresIn` in seconds, so a client can tell when to refresh.
- **#28 — admin role.** `users.role` (V15), carried as a `role` claim in the access token (missing/unknown = USER). The nine POST trigger endpoints (ingest/compute/generate/backtest) require ADMIN: anonymous gets 401, a non-admin 403. The first admin is created at startup from `ADMIN_EMAIL`/`ADMIN_PASSWORD` only if no such account exists, and never promotes an existing one (emails aren't verified).
- **#29 — `aws` profile, admin wiring, alarm.** The task now runs `SPRING_PROFILES_ACTIVE=aws` (JSON logs at INFO) instead of `local` (plain text at DEBUG with every SQL statement). `IngestionRunService` now logs start/success/failure inside the correlation-id scope — previously no job logged anything inside its run, so a correlationId search found nothing. Terraform generates an `admin-password` secret and wires `ADMIN_EMAIL`/`ADMIN_PASSWORD` onto the task. New `HealthyHostCount < 1` alarm (missing data = breaching) for the outage the 5xx and unhealthy-host alarms stay silent on.
- **#30 — HTTP timeouts.** `fantasyiq.http-client` connect 5s / read 10s on every vendor `RestClient`, validated at startup. A hung vendor call used to stall a job forever (retry and the circuit breaker only react to failures). With 3 attempts a hung call surfaces after ~30s as a 503 and a FAILED `ingestion_runs` row.
- **#31 — scheduled scoring.** `IngestionScheduler` runs scoring daily at 07:30 (server zone, UTC on ECS), after players 06:00 / games 06:15 / injuries 07:00. `CurrentWeekResolver` picks the week from the schedule (earliest game with kickoff within the last 6h or the next 10 days); nothing in the offseason. Verified by firing the real job with a shifted JVM clock.
- **#32 — N+1 on recommendations.** `@EntityGraph` on the two list queries: a cache miss for 800 recommendations went 1,601 SQL statements → 1 and 1.16s → 0.40s with a byte-identical response; `tune-weights` 2,401 → 801.

## Pending: first apply of PR #29

The infra/profile changes in #29 are verified locally (the real image under the `aws` profile against throwaway Postgres/Redis; `terraform validate` + `fmt -check` in a container) but **not applied to real AWS**. At the next AWS session, in order:

1. `terraform apply`, push an image (by hand or via the pipeline — the new registry starts empty), set the two vendor API keys (`aws secretsmanager put-secret-value`) and restart the task.
2. **The task starts** — this proves the execution role can read the new `admin-password` secret (if it can't, ECS never starts the task).
3. Fetch the admin password: `aws secretsmanager get-secret-value --secret-id fantasyiq/admin-password --query SecretString --output text`, then log in at `/api/auth/login` as `admin@fantasyiq.dev` (new password every apply, recreated with the database).
4. Run an ingestion job as admin, then a CloudWatch Logs Insights query for its `correlationId` — it should return the start and finish lines.
5. The new no-healthy-hosts alarm should stay quiet through a normal ~4-minute boot, and fire after 5 minutes if no image was pushed.
6. A fresh database is empty: re-run the ingestion jobs and `/generate` (or wait for 07:30 UTC) before the rankings show anything.

## What remains

**Product:**
- **Phase 8, the frontend** — nothing built. The big one.
- **No START/SIT verdict** — `GET /recommendations/start-sit` returns a score and a LOW/MEDIUM/HIGH confidence (just a count of available factors, which nothing consumes), no verdict. The PRD wanted START / SIT / TOSS-UP.
- **A full week's response is ~400 KB** (800 players, no pagination); a cache hit still takes ~0.12s for that payload. A UI should request one position at a time, or the API should gain a `limit`.
- Waiver-wire analyzer (`analytics/waiver` is an empty placeholder); K/DST support (needs a different ESPN endpoint).

**Backend gaps:**
- Odds and weather ingestion have no cron, so scheduled scoring omits those factors unless ingested by hand; no Sunday-morning refresh (would need injury ingestion to run again then, in an `America/New_York` zone).
- `/actuator/prometheus` and `/actuator/metrics` are readable by any authenticated user (only the nine trigger endpoints are admin-only).
- Rate limiting keys on `request.getRemoteAddr()`, which behind the ALB is the ALB's address — probably one shared bucket on AWS (no forwarded-headers config).
- Role changes take up to the 15-minute token lifetime to apply.
- Trade analyzer: the injury factor's -1000 makes a short-term OUT player worth catastrophic value over a rest-of-season horizon; `computeReplacementLevels` is N+1 (~3.3s per request).
- Backtest: N+1 inside `gatherFactors` (~18 min for a full season), and `RecommendationMatcher` does one lookup per recommendation (~800 statements for a week).
- `refresh_tokens` is never cleaned up; no `EspnInjuryProvider` contract test.
- The model: `MATCHUP` and `USAGE` show ~no signal in backtests, `VEGAS` and `WEATHER` have never been backtestable (no historical data), and the composite (0.31) is below the best single factor (0.54) — not a like-for-like comparison (different subsets of players), but a hint the weaker factors add noise. Weights are hand-picked except `RECENT_PERFORMANCE`.

**Infra:**
- App-level Micrometer→CloudWatch bridge (job-failure and circuit-breaker alarms) — needs `micrometer-registry-cloudwatch2` and a new IAM *task* role.
- Remote Terraform state (it's a local, gitignored file containing secrets; losing it orphans the resources).
- HTTPS (ALB is HTTP-only; needs a domain + ACM certificate before real users).
- The pipeline updates the service's task definition outside Terraform, and `aws_ecs_service` has no `ignore_changes` on it, so a later `terraform apply` would likely try to revert it (never hit in practice, since every session ends in a destroy).
- The Deploy workflow runs on every push to `main` (including docs-only and direct pushes) and fails when nothing is deployed, because the OIDC role doesn't exist. A re-run after a failure *past* the push step would likely fail again at the push (immutable image tags; reasoned, not observed).
- Running more than one app instance would need a Redis-backed rate limiter, a lock on the cron jobs, and capped Hikari pools.
- `README.md`/`docs/` are still frozen at Phase 0.

## Recommended next steps

1. **Phase 8, the frontend.** Decisions to settle first: the stack (React was assumed in the docs, nothing's chosen); the first screen (suggested: login → a week's ranked list with a position filter → a player page showing the factor breakdown); where the START/SIT verdict comes from (backend, or derived in the UI from rank within position); how to avoid the 400 KB response.
2. **The first real AWS apply of #29**, following the checklist above, before building on it.
3. Smaller items from "What remains" as they become relevant.

Remote branches: all 32 branches from merged PRs (`phase-1` through `phase-8`) still exist on origin — cleanup is deferred, nothing deletes them automatically.

## Working in this environment

- **Local Gradle is unusable** (JVM loopback-socket issue, see `CLAUDE.md`). What works instead, with Docker Desktop running: `docker build --target build -t x .` compiles everything, then `docker run --rm x sh -c "./gradlew test --tests '*SomeTest'"` runs unit tests in Linux. **Testcontainers integration tests (`*IT`) cannot run that way** — CI is their only check, so a change touching them is only fully verified once CI is green.
- Real-behavior checks done so far used the actual image against throwaway Postgres/Redis containers on a private Docker network. To log Hibernate SQL, set it via `SPRING_APPLICATION_JSON={"logging.level.org.hibernate.SQL":"DEBUG"}` — Spring lowercases env var names, so `LOGGING_LEVEL_ORG_HIBERNATE_SQL` silently does nothing.
- Git: one branch per PR (`phase-N/short-description`), PRs created by hand from the compare URL (no `gh` CLI), no AI attribution anywhere. This file is committed straight to `main`.
