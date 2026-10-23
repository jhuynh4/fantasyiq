# Current Work

## Status: backend complete through Phase 7 plus a hardening pass (PRs #27-#32); the Phase 8 frontend is built (PRs #33-#43) and was proven on real AWS in session 1 (2026-10-21); **start/sit scoring was rebuilt as v2 on 2026-10-22 (branch `phase-9/scoring-v2`)**

Phases 1-7 are merged to `main`, and so is all of Phase 8. After a full backend/cloud review, a hardening pass (PRs #27-#32, below) closed the gaps the review found. **Nothing is currently running in AWS**: session 1 applied the whole stack (64 resources), proved it, and destroyed it (`terraform state list` was empty afterwards). The account is on a credit-limited plan, hence the apply, verify, destroy rhythm. See "AWS session 1" for what was proven, what was found and fixed on the way, and what is still unverified.

The frontend (five screens, hosted on S3 + CloudFront) is finished; its design came from a Figma file, read through the Figma MCP. **The biggest open problem is now the model's scoring**, which looked wrong on real current data (see "What remains").

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
- **Slice 5 (#40)** — trade analyzer at `/trade?give=<ids>&get=<ids>` (comma-separated, up to 5 per side, in the address so a trade is a shareable link; a link with both sides filled analyzes on load). Not in the Figma, so it is designed from the existing screens. "You give" is side A and "You get" side B; the server's `valueDelta` is A minus B, so the screen reads it from the side you receive and phrases it without a verdict. Analysis only runs on the button (the request takes ~3s because it scores every player to find replacement levels) with a waiting message; changing the players hides the stale result. Players are chosen with the compare screen's search (any scored position). Per player: score, replacement level, value above replacement (negative when below replacement), and factor contributions. A ruled-out player gets a note that the injury penalty is applied over the whole rest of the season (the known backend gap). A "how value is worked out" card explains the three factors used and the replacement cutoffs. "Trade" added to the navigation.
- **Slice 6 (hosting, split into three PRs):** (a) `phase-8/frontend-hosting` — `infra/frontend.tf` (private S3 bucket `fantasyiq-frontend-<account id>`, CloudFront with origin access control; the default behavior serves the bucket, `/api/*` goes to the ALB with caching off and every viewer header forwarded; a CloudFront Function serves `index.html` for paths that are not files, instead of the usual distribution-wide 403/404 error mapping, which would also rewrite the API's own 404/403 into the HTML page), a `frontend` job in `deploy.yml` (build, upload `assets/*` with a one-year immutable cache header and everything else with `no-cache`, invalidate), narrow S3/CloudFront permissions on the deploy role, and `workflow_dispatch` so a deploy can be started by hand after an apply. Proven live in session 1. (b) `phase-8/rate-limit-client-address` — the rate limiter reads the real visitor from `X-Forwarded-For` (see Known gaps); (c) `phase-8/alb-behind-cloudfront` — the ALB's security group admits only CloudFront's managed prefix list (`com.amazonaws.global.cloudfront.origin-facing`), and the listener's default action is a 403 unless the request carries a secret `X-Origin-Verify` header that CloudFront adds (a Terraform-generated `random_password`; the address ranges cover every CloudFront distribution, so the header is what stops someone else's distribution). The old `app_url` output (the ALB's own address) is gone because that address no longer answers. Proven live in session 1 (the app works through CloudFront, and the ALB's own address timed out from the internet).
- **Frontend plan complete** (slice 6 merged as #41, #42, #43). Follow-ups are under "What remains".

Where the UI differs from the Figma, on purpose: no "generated at / model version" line (the API doesn't provide them), the legend names the six real factors instead of Passing/Rushing/Form/Opportunity, and the confidence text states the real rule (count of factors with data).

Node isn't installed locally at a usable version (host has 19): run npm and Vite in a `node:22-alpine` container. The dev server needs `-e VITE_USE_POLLING=1` there, or edits don't reach it. See `frontend/README.md`.

## AWS session 1 (2026-10-21): results

The whole of `docs/runbooks/aws-session.md` was followed once, from a Windows PowerShell prompt. It took a few hours, mostly waiting; the stack was destroyed at the end.

**Proven live**
- `terraform apply`: 64 resources, no errors (the CloudFront prefix-list rule fit inside the security group's rule limit).
- The Deploy workflow, started by hand with `workflow_dispatch`: both jobs passed (the GitHub-to-AWS OIDC sign-in, image build and push, ECS waiting until the new task was healthy, the frontend upload and CloudFront invalidation).
- The backend under the `aws` profile (PR #29): one task steady, JSON logs, Flyway applied all 15 migrations to the empty database, `Started FantasyIqApplication in 85.9 seconds` (inside the 180 s health grace period), and the admin account was created, which proves the execution role can read the `admin-password` secret.
- Through the CloudFront address: the sign-in page over HTTPS, a reload on a deep link, `/api` answering JSON rather than the HTML page, the cache headers (`no-cache` on the page, immutable on built files), and the ALB's own address not answering. These were reported as passing; the command output was not captured.
- Admin sign-in through the whole path (CloudFront, secret header, ALB, task, RDS), and ingestion of the current season: players 2,553 records in 42 s, games 272 in 14 s, injuries 460 in 9.5 s, box scores 2,382 in 40 s (all `200`, inside CloudFront's 60 s limit), then defense-vs-position for weeks 1-6, weather, odds, and scoring for week 7 and for weeks 1-6.
- Both vendor factors reached the scores: a quarterback's breakdown listed `VEGAS` and `WEATHER` next to the other four.
- The app: opened on 2026 week 7 by itself (first live proof of `/api/weeks/current`), the trade analyzer took 2-4 s on the small Fargate task, compare only offered same-position players, an OUT player showed as out, and a Questionable player showed as questionable. Nothing felt slow.

**Found and fixed during the session**
- Every ESPN job failed with `403 Forbidden: Access Denied` (Akamai). With no `User-Agent` set, the JDK's older HTTP class sends `Java/21.x`, and ESPN's edge refuses that name from cloud addresses (never from a home network, which is why it was never seen). From an AWS address `Java/21.0.5` got 403 while `Java-http-client/21.0.5`, `FantasyIQ/1.0` and `curl` got 200. Fixed in #47: every vendor call sends `FantasyIQ/1.0`.
- The failure was invisible: the logs showed only "ESPN teams endpoint unavailable". Fixed in #46: a failed run now logs the full cause chain and `rootCause=...`.
- I predicted the box-score job would answer `504` after 60 s and that estimate was wrong (it took 40 s). The runbook now has the measured timings.

**Not verified (do these at the start of the next session; each takes a minute)**
- The per-visitor rate limit from a second network (eleven bad logins give ten `401`s then `429`, and a phone on mobile data can still sign in), which tests the `X-Forwarded-For` fix (#42).
- A normal (non-admin) account gets `403` on an admin endpoint.
- A Logs Insights query by `correlationId` returns that run's start and finish lines.
- No SQL statements in the logs (the count command was not reported back).
- **Alarm emails: the SNS subscription was never confirmed.** The confirmation email never arrived, and the alarm actions showed "Pending confirmation" at the end, so no alarm could have notified anyone. Check the address in `infra/terraform.tfvars`, search spam for `sns.amazonaws.com`, and confirm the subscription early next time (the runbook step 3 now says how). The state the no-healthy-hosts alarm reached was not recorded either.

## AWS session 2 (2026-10-23): results

Second real apply, with everything built since session 1: scoring v2.1, the trade and week-picker changes, the injury wording fix, the early-season blend. Filled in as the checks were run; blanks were not run or not reported back.

**Worked:** apply, image and frontend deploy, vendor keys, sign-in through CloudFront, ingestion of the 2026 season, defense-vs-position for the played weeks, weather, odds, scoring for week 7 and the earlier weeks, rankings with the new week picker (only weeks with scores are offered).

**Found:**
- **Weather and odds returned `503` at first.** The secrets are handed to the container only when it starts, and the keys were stored after the task was already running, so the app had empty keys and the vendors answered `401`. Fix: store the keys before the deploy, or `aws ecs update-service --force-new-deployment` afterwards (runbook step 6 now says so).
- **Access tokens last 15 minutes**, and a long session outruns them constantly; every `401` in a loop of `post` calls was this. Repeat the `TOKEN=` line.
- **Git Bash rewrites arguments that start with `/`**, so `aws logs tail /ecs/fantasyiq` fails with an `InvalidParameterException` about the log group name. Prefix the command with `MSYS_NO_PATHCONV=1`.
- **Scoring one week took 18.9 s warm and 45-59 s on the first runs after a restart** (ALB `TargetResponseTime`, and `Ingestion run succeeded: source=COMPUTED_START_SIT ... durationMs=22279` in the logs). The task is 0.5 vCPU with no JVM memory flags (default heap is a quarter of the container, ~256 MB); CPU sat at 100% for that minute and memory stayed flat at ~42%. The slowest run was 59.5 s, inside CloudFront's 60 s limit by half a second, so run generates one at a time and warm the app with a few ordinary requests first. The real fix is fewer queries per week (they are one-per-player), a code change.
- **The rankings read path is fine**: 0.14-0.21 s for one position, 0.1-0.17 s with the breakdown, ~50 KB for tight ends. Reads decode the whole week's cached list (all positions) every time; caching per position would cut that.
- **An intermittent stall on the API path through CloudFront, cause not found** (below).
- The week picker lists only weeks that have a START_SIT row (`GET /api/recommendations/periods`); a week "disappearing" from it means no scores, not lost data.

**The intermittent stall.** About 2-4% of requests to `/api/*` through CloudFront waited from 1 s to 2 minutes for the first byte (seen: 1, 2, 4, 10, 16, 31, 32, 36, 61, 76, 92, 120 s). Evidence, in the order it was gathered:
- `curl` breakdown of a stalled request: DNS, TCP connect and TLS to CloudFront all ~0.01-0.05 s; time to first byte 30.5 s; then the whole body in 0.06 s. So the stall is after the request reaches CloudFront.
- ALB `TargetResponseTime` stayed 0.01-0.06 s through every stall (the app answered in milliseconds), memory 42% and ECS CPU ~1% while a 92 s read was in flight. The ALB `RequestCount` for the minute of a 30 s stall was one short, and the next minute one over: the request reached the load balancer ~30 s late. (Caution: `RequestCount` is probably attributed at completion, so this alone cannot tell a CloudFront-to-ALB delay from an ALB-to-task delay.)
- A static page (`/`, served from S3) never stalled in ~150 requests; the API path did (6 in ~150), including a request the app rejects instantly with a `401` and which uses neither Redis nor the database. Not the cache, not the database, not the scoring code, not the client's network.
- A temporary ALB listener rule answering `/api/pingtest` itself (a fixed `200` `pong`, no target involved) still stalled: 1.07, 0.98 and 2.57 s in 200 requests, against 2.2, 16.2, 2.0, 1.7 and 4.0 s on the app path. So at least part of it happens at or before the load balancer.
- Several stall lengths sit on the TCP SYN retransmit schedule (1, 3, 7, 15, 31, 63 s since the first attempt), which looks like occasional dropped connection attempts being retried.
- **Setting CloudFront's origin `connection_timeout` to 3 s (3 attempts) did not help**: stalls of 120, 76, 36 and 16 s followed, none bounded by 9 s. Not merged; the branch was dropped.
- **Not run:** temporarily opening the ALB security group's port 80 to 0.0.0.0/0 (the listener's secret-header rule still blocks everyone but CloudFront) to test whether the CloudFront prefix-list rule drops some CloudFront connections. This is the next experiment, with CloudFront access logs and ALB access logs (`request_processing_time` vs `target_processing_time`) turned on first so a stall can be attributed to a leg. If neither shows it, open an AWS support case with these numbers.

How to reproduce and split a stall: runbook "Appendix: finding where a slow request is slow".

**Verification list results (runbook steps 3, 8-10):**
- Alarm email subscription confirmed: _not reported_
- Non-admin account gets `403` on `POST /api/players/ingest`: _not reported_
- Login rate limit (ten `401`s then `429`, and a different network still works): _not reported_
- Player page shows Weather and Vegas factors for an outdoor game (vendor keys end to end): _not reported_
- An injured-reserve player shows as ruled out (the `INJURED RESERVE` fix on real ESPN data): _not reported_
- Trade timing on the small task, compare, sign-out/reload: _not reported_
- Dashboard and alarms: _not reported_

## What remains

**Product:**
- **The model's scoring: v2.1 (see `CLAUDE.md` "Scoring v2").** 2025 backtest 0.306 (v1) -> 0.648 (v2) -> **0.667** (v2.1: last season's average counts as up to 4 extra games, so week 1 now has rankings, 0.69 correlation on 282 players); RB 0.658, TE 0.632, WR 0.626, QB 0.462. Trade values use the same blend. Still open: `VEGAS`/`WEATHER` are unvalidated guesses (no historical data), QB looks weak (0.46) but is near its ceiling (see `CLAUDE.md` "Why QB looks weak": a single QB game is mostly noise; among starters even an oracle reaches only 0.25), rookies get no row in week 1 (no games anywhere), and the injury shares are judgement calls. After deploying, regenerate the current week; old rows are purged automatically on the next run for that week.
- **The weather factor**: the forecast values are now in the text (`8 mph wind, 20% chance of precipitation -- no penalty`), fixed in `phase-9/ui-followups`. Still open: temperature is not part of the factor, and the thresholds (wind over 15 mph for QB/WR/TE, more than 50% chance of precipitation) were never backtested (no historical weather).
- **The week picker** now offers only weeks that have scores (`GET /api/recommendations/periods`, `useAvailablePeriods`); the week on show stays selectable and is labelled "no scores yet", and switching season moves to a scored week. If the request fails the picker falls back to all 18 weeks. Earlier weeks still use today's injury status.
- **No START/SIT verdict** — `GET /recommendations/start-sit` returns a score and a LOW/MEDIUM/HIGH confidence (just a count of available factors, which nothing consumes), no verdict. The PRD wanted START / SIT / TOSS-UP.
- **A full week's response is ~400 KB** (800 players, no pagination); a cache hit still takes ~0.12s for that payload. A UI should request one position at a time, or the API should gain a `limit`.
- Waiver-wire analyzer (`analytics/waiver` is an empty placeholder); K/DST support (needs a different ESPN endpoint).

**Backend gaps:**
- Odds and weather ingestion have no cron, so scheduled scoring omits those factors unless ingested by hand; no Sunday-morning refresh (would need injury ingestion to run again then, in an `America/New_York` zone).
- `/actuator/prometheus` and `/actuator/metrics` are readable by any authenticated user (only the nine trigger endpoints are admin-only).
- ~~Rate limiting keys on the ALB's address~~ — fixed in slice 6b (`ClientAddress`, `forwarded-hops: 2` under the `aws` profile): the visitor is the second entry from the right of `X-Forwarded-For`. Until 6c locks the ALB to CloudFront, a caller who goes straight to the ALB (not through CloudFront) has only one entry in the header, so they all fall back to one shared bucket (not spoofable, just shared).
- Role changes take up to the 15-minute token lifetime to apply.
- ~~Trade analyzer: -1000 injury penalty and N+1 replacement levels~~ — fixed in `phase-9/trade-v2` (season-average value, injury as a share of the season, one query per position; ~0.9s -> ~0.25s). Also found there: ESPN's real status wording is `INJURED RESERVE` and `SUSPENSION`, which the injury factor did not recognize (all IR players were read as healthy in start/sit too); fixed.
- Backtest: N+1 inside `gatherFactors` (~18 min for a full season), and `RecommendationMatcher` does one lookup per recommendation (~800 statements for a week).
- ~~`GET /api/players/{id}` with an id that isn't a UUID returned 500~~ — fixed in #38 (400).
- `refresh_tokens` is never cleaned up; no `EspnInjuryProvider` contract test.
- The model (v2): see the first bullet under "Product". The old v1 finding (composite 0.31 below its best single factor 0.54) is what led to the rebuild.

**Infra:**
- **Find the intermittent API stall** (see "AWS session 2"): turn on CloudFront and ALB access logs, then the open-port experiment; 2-4% of API requests stall for 1 s to 2 min and the cause is somewhere between CloudFront and the load balancer.
- **Confirm the alarm email subscription** (see "AWS session 1"): until it is confirmed, no alarm notifies anyone.
- App-level Micrometer→CloudWatch bridge (job-failure and circuit-breaker alarms) — needs `micrometer-registry-cloudwatch2` and a new IAM *task* role.
- Remote Terraform state (it's a local, gitignored file containing secrets; losing it orphans the resources).
- HTTPS (ALB is HTTP-only; needs a domain + ACM certificate before real users).
- The pipeline updates the service's task definition outside Terraform, and `aws_ecs_service` has no `ignore_changes` on it, so a later `terraform apply` would likely try to revert it (never hit in practice, since every session ends in a destroy).
- The Deploy workflow runs on every push to `main` (including docs-only and direct pushes) and fails when nothing is deployed, because the OIDC role doesn't exist. A re-run after a failure *past* the push step would likely fail again at the push (immutable image tags; reasoned, not observed).
- Running more than one app instance would need a Redis-backed rate limiter, a lock on the cron jobs, and capped Hikari pools.
- `README.md`/`docs/` are still frozen at Phase 0.

## Recommended next steps

1. **Model follow-ups:** nothing with a measured payoff is left. QB accuracy was investigated and is near its ceiling (see above); the injury and weather shares need history that doesn't exist. If the model is revisited, the open idea is showing uncertainty (a QB's typical single-game range is about +-8 PPR) so the score isn't read as precise.
2. ~~The small follow-ups (weather text, week picker)~~ done in `phase-9/ui-followups`.
3. **The next AWS session** (start by turning on CloudFront and ALB access logging, see "AWS session 2"): only when something needs proving on AWS. Do the "Not verified" list first; start the session by confirming the alert email.

Remote branches: all 32 branches from merged PRs (`phase-1` through `phase-8`) still exist on origin — cleanup is deferred, nothing deletes them automatically.

## Working in this environment

- **Local Gradle is unusable** (JVM loopback-socket issue, see `CLAUDE.md`). What works instead, with Docker Desktop running: `docker build --target build -t fq-build .` compiles everything. **The whole suite, Testcontainers ITs included, also runs locally** (found 2026-10-22): `docker run --rm -u root -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true fq-build sh -c 'echo "api.version=1.44" > /root/.docker-java.properties; ./gradlew test --no-daemon'` (218 tests, ~1.5 min; in Git Bash prefix with `MSYS_NO_PATHCONV=1`). Both `-u root` and the `api.version` line are needed (Docker Desktop's engine 29 rejects the old docker-java client). CI is still the required check.
- Real-behavior checks done so far used the actual image against throwaway Postgres/Redis containers on a private Docker network. To log Hibernate SQL, set it via `SPRING_APPLICATION_JSON={"logging.level.org.hibernate.SQL":"DEBUG"}` — Spring lowercases env var names, so `LOGGING_LEVEL_ORG_HIBERNATE_SQL` silently does nothing.
- Git: one branch per PR (`phase-N/short-description`), PRs created by hand from the compare URL (no `gh` CLI), no AI attribution anywhere. Everything goes through a PR, this file included.
- **Windows PowerShell on this machine** parses some arguments differently from bash: `terraform plan -out=x.plan` must be quoted (`"-out=x.plan"`), and `curl` is a different command from `curl.exe`. The runbook's appendix has the PowerShell versions of everything.
- The local database holds only the 2025 season. To get the current season locally, run the same ingestion steps as the runbook against the local stack (players, games, injuries, box scores, defense-vs-position for each played week, then `/generate`).
