# Current Work

## Status: backend complete through Phase 7 plus a hardening pass (PRs #27-#32) — Phase 8 (frontend) in progress, slices 0-2 built (see "Phase 8 progress")

Phases 1-7 are merged to `main`. After a full backend/cloud review, a hardening pass (PRs #27-#32, below) closed the gaps the review found. **Nothing is currently running in AWS** — everything was `terraform destroy`'d to stop billing (the account is on a credit-limited plan, hence the apply → verify → destroy rhythm). The infrastructure and pipeline code were proven live earlier; **PR #29's infra changes have never run against real AWS** (see "Pending: first apply of PR #29").

The frontend is being built in slices (see "Phase 8 progress"). Its design comes from a Figma file, read through the Figma MCP.

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

## Phase 8 progress (frontend, `frontend/`)

Decided: React + Vite + TypeScript static app; sign-in, a week's ranked list with position tabs, player detail with the factor breakdown, head-to-head compare, trade analyzer. No absolute START/SIT verdict yet (show rank within position plus compare); one position per request. Hosting plan: S3 + CloudFront with `/api/*` routed to the ALB, so the browser sees one HTTPS origin.

- **Slice 0 (#33)** — `GET /api/weeks/current`: `{season, week}`, or 204 when there is no current week.
- **Dev stack (#34)** — `docker compose --profile app up -d --build` runs Postgres, Redis and the backend (admin `admin@fantasyiq.dev` / `local-admin-passphrase`). The local database holds the full 2025 season but nothing for the current week, so build and check screens against 2025.
- **Slice 1 (#35)** — Vite app, Figma design tokens (light/dark), API client (in-memory access token, refresh token in localStorage, refresh ahead of expiry, one shared refresh, one retry on 401), sign-in/register, app shell, `frontend` CI job.
- **Slice 2 (#36)** — the rankings screen: season/week pickers (defaulting to the current week, falling back to the end of the latest season), position tabs, the ranked table with a factor-shape bar per row, legend, and a confidence guide. Address holds the state (`/?season=2025&week=10&position=WR`). The first 50 rows show, with "show all".
- **Slice 3 (#37)** — player detail at `/players/:id?season=&week=`. Rows in the rankings link there (whole row is the target). It shows the profile (`GET /api/players/{id}`), the player's rank within their position and score, "Why this score" (each factor's contribution as a signed bar, plus the backend's own `narrative` text), the full arithmetic line, and a side card explaining confidence. The factors come from the same start-sit query the rankings screen runs (position taken from the profile), so arriving from the list costs no extra request; a player with no score that week gets a message. A ruled-out player's -1000 injury penalty is shown as "Out" with a full-lane bar.
- **Slice 4 (#39)** — compare at `/compare?season=&week=&a=<id>&b=<id>` (ids in the address, so a comparison is a shareable link). The player page's "Compare player" button starts one with that player as the first. Each side is a card (profile, score, rank, confidence) with a search box (`GET /api/players/search`, results limited to the model's four positions and, once one player is chosen, to that position). Below: the score gap, a factor-by-factor table (each side's signed contribution, the difference, and a sentence), both players' full sums, and a "main trade-off" paragraph. The sentences and the paragraph are generated from the numbers (`features/compare/compare.ts`), not canned text. Players at different positions are allowed (via the address) but get a warning. A factor one player has no data for counts as zero and is labelled "No data".
- **Slice 5 (this branch)** — trade analyzer at `/trade?give=<ids>&get=<ids>` (comma-separated, up to 5 per side, in the address so a trade is a shareable link; a link with both sides filled analyzes on load). Not in the Figma, so it is designed from the existing screens. "You give" is side A and "You get" side B; the server's `valueDelta` is A minus B, so the screen reads it from the side you receive and phrases it without a verdict. Analysis only runs on the button (the request takes ~3s because it scores every player to find replacement levels) with a waiting message; changing the players hides the stale result. Players are chosen with the compare screen's search (any scored position). Per player: score, replacement level, value above replacement (negative when below replacement), and factor contributions. A ruled-out player gets a note that the injury penalty is applied over the whole rest of the season (the known backend gap). A "how value is worked out" card explains the three factors used and the replacement cutoffs. "Trade" added to the navigation.
- **Slice 6 (hosting, split into three PRs):** (a) `phase-8/frontend-hosting` — `infra/frontend.tf` (private S3 bucket `fantasyiq-frontend-<account id>`, CloudFront with origin access control; the default behavior serves the bucket, `/api/*` goes to the ALB with caching off and every viewer header forwarded; a CloudFront Function serves `index.html` for paths that are not files, instead of the usual distribution-wide 403/404 error mapping, which would also rewrite the API's own 404/403 into the HTML page), a `frontend` job in `deploy.yml` (build, upload `assets/*` with a one-year immutable cache header and everything else with `no-cache`, invalidate), narrow S3/CloudFront permissions on the deploy role, and `workflow_dispatch` so a deploy can be started by hand after an apply. Verified with `terraform validate`/`fmt -check` and by exercising the function on sample paths; **not applied to real AWS**. (b) `phase-8/rate-limit-client-address` — the rate limiter reads the real visitor from `X-Forwarded-For` (see Known gaps); (c) `phase-8/alb-behind-cloudfront` — the ALB's security group admits only CloudFront's managed prefix list (`com.amazonaws.global.cloudfront.origin-facing`), and the listener's default action is a 403 unless the request carries a secret `X-Origin-Verify` header that CloudFront adds (a Terraform-generated `random_password`; the address ranges cover every CloudFront distribution, so the header is what stops someone else's distribution). The old `app_url` output (the ALB's own address) is gone because that address no longer answers. Verified with `terraform validate`/`fmt -check` only; **not applied**.
- **Not yet built:** nothing in the frontend plan; nothing of slice 6 is left to write, only the AWS session below.

Where the UI differs from the Figma, on purpose: no "generated at / model version" line (the API doesn't provide them), the legend names the six real factors instead of Passing/Rushing/Form/Opportunity, and the confidence text states the real rule (count of factors with data).

Node isn't installed locally at a usable version (host has 19): run npm and Vite in a `node:22-alpine` container. The dev server needs `-e VITE_USE_POLLING=1` there, or edits don't reach it. See `frontend/README.md`.

## Pending: first apply of PR #29

**The step-by-step for the whole session (apply, deploy, every check below, destroy) is `docs/runbooks/aws-session.md`; follow that, and use the list below as the summary of what has to be proven.** The infra/profile changes in #29 are verified locally (the real image under the `aws` profile against throwaway Postgres/Redis; `terraform validate` + `fmt -check` in a container) but **not applied to real AWS**. At the next AWS session, in order:

1. `terraform apply`, push an image (by hand or via the pipeline — the new registry starts empty), set the two vendor API keys (`aws secretsmanager put-secret-value`) and restart the task.
2. **The task starts** — this proves the execution role can read the new `admin-password` secret (if it can't, ECS never starts the task).
3. Fetch the admin password: `aws secretsmanager get-secret-value --secret-id fantasyiq/admin-password --query SecretString --output text`, then log in at `/api/auth/login` as `admin@fantasyiq.dev` (new password every apply, recreated with the database).
4. Run an ingestion job as admin, then a CloudWatch Logs Insights query for its `correlationId` — it should return the start and finish lines.
5. The new no-healthy-hosts alarm should stay quiet through a normal ~4-minute boot, and fire after 5 minutes if no image was pushed.
6. A fresh database is empty: re-run the ingestion jobs and `/generate` (or wait for 07:30 UTC) before the rankings show anything.
7. **Frontend (slice 6a):** the bucket is empty after an apply. Run the Deploy workflow by hand from `main` (Actions tab, "Run workflow"), then open `terraform output frontend_url`. Check: the sign-in page loads over HTTPS; sign in works (the request goes `/api/auth/login` through CloudFront to the ALB); a reload on `/compare` or a player page does not 404; a request for a player that does not exist (`/api/players/<random uuid>`) still returns the JSON 404, not the HTML page; the browser keeps `index.html` fresh (response header `cache-control: no-cache`) while files under `/assets/` are cached for a year. CloudFront takes ~5 minutes to create and 10+ to delete, so the destroy at the end of the session is slower than before. **Slice 6c checks:** the app works through `frontend_url` at all (that proves the secret header and the security group rule are right; if every `/api` call returns 504 or 403, look at the ALB listener rule and the origin custom header first); the ALB's own address (`aws elbv2 describe-load-balancers`) does not answer from the internet (it times out); the ALB security group accepted the prefix-list rule (a rule-limit error at apply time would mean the 55-entry list does not fit the default quota of 60). The old habit of `curl`ing the ALB's `/actuator/health` no longer works from outside: the ECS service's own health status (and the target group's) is where to look.

## What remains

**Product:**
- **Phase 8, the frontend** — slices 0-2 built; player detail, compare, trade analyzer and hosting remain.
- **No START/SIT verdict** — `GET /recommendations/start-sit` returns a score and a LOW/MEDIUM/HIGH confidence (just a count of available factors, which nothing consumes), no verdict. The PRD wanted START / SIT / TOSS-UP.
- **A full week's response is ~400 KB** (800 players, no pagination); a cache hit still takes ~0.12s for that payload. A UI should request one position at a time, or the API should gain a `limit`.
- Waiver-wire analyzer (`analytics/waiver` is an empty placeholder); K/DST support (needs a different ESPN endpoint).

**Backend gaps:**
- Odds and weather ingestion have no cron, so scheduled scoring omits those factors unless ingested by hand; no Sunday-morning refresh (would need injury ingestion to run again then, in an `America/New_York` zone).
- `/actuator/prometheus` and `/actuator/metrics` are readable by any authenticated user (only the nine trigger endpoints are admin-only).
- ~~Rate limiting keys on the ALB's address~~ — fixed in slice 6b (`ClientAddress`, `forwarded-hops: 2` under the `aws` profile): the visitor is the second entry from the right of `X-Forwarded-For`. Until 6c locks the ALB to CloudFront, a caller who goes straight to the ALB (not through CloudFront) has only one entry in the header, so they all fall back to one shared bucket (not spoofable, just shared).
- Role changes take up to the 15-minute token lifetime to apply.
- Trade analyzer: the injury factor's -1000 makes a short-term OUT player worth catastrophic value over a rest-of-season horizon; `computeReplacementLevels` is N+1 (~3.3s per request).
- Backtest: N+1 inside `gatherFactors` (~18 min for a full season), and `RecommendationMatcher` does one lookup per recommendation (~800 statements for a week).
- `GET /api/players/{id}` with an id that isn't a UUID returns 500 (the catch-all handler) instead of 400; the UI checks the format first.
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

1. **Phase 8, the frontend** — slice 6 (hosting) is written; what is left is the AWS session (see the checklist), 
2. **The first real AWS apply of #29**, following the checklist above, before building on it.
3. Smaller items from "What remains" as they become relevant.

Remote branches: all 32 branches from merged PRs (`phase-1` through `phase-8`) still exist on origin — cleanup is deferred, nothing deletes them automatically.

## Working in this environment

- **Local Gradle is unusable** (JVM loopback-socket issue, see `CLAUDE.md`). What works instead, with Docker Desktop running: `docker build --target build -t x .` compiles everything, then `docker run --rm x sh -c "./gradlew test --tests '*SomeTest'"` runs unit tests in Linux. **Testcontainers integration tests (`*IT`) cannot run that way** — CI is their only check, so a change touching them is only fully verified once CI is green.
- Real-behavior checks done so far used the actual image against throwaway Postgres/Redis containers on a private Docker network. To log Hibernate SQL, set it via `SPRING_APPLICATION_JSON={"logging.level.org.hibernate.SQL":"DEBUG"}` — Spring lowercases env var names, so `LOGGING_LEVEL_ORG_HIBERNATE_SQL` silently does nothing.
- Git: one branch per PR (`phase-N/short-description`), PRs created by hand from the compare URL (no `gh` CLI), no AI attribution anywhere. This file is committed straight to `main`.
