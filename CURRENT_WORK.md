# Current Work

## Status: Phase 7 fully complete — infrastructure and CI/CD pipeline both proven live, then torn down

All of Phase 7 is merged to `main` (PRs #18-25) and proven against a real AWS account end to end, twice: once for the infrastructure alone, and again after adding the GitHub Actions deploy pipeline. A real `git push` → merge to `main` now genuinely builds the image, pushes it to ECR, and redeploys ECS automatically, with ECS confirming the new version healthy before the pipeline reports success. Everything was `terraform destroy`'d afterward (45 resources) to stop billing — **nothing is currently running in AWS**. All of the infrastructure and pipeline code is real, complete, and already proven; bringing it back is just `terraform apply`.

## What's been completed (Phases 1-6, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2** — games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle, `defense_vs_position_stats`, `weather_forecasts`, `betting_lines`.

**Phase 3** — start/sit recommendation engine (6 factors), player trending endpoint, backtest validation, weight tuning + recent-performance factor (took real predictive correlation from `0.0137` to `0.306`).

**Phase 4** — Redis caching (`GET /recommendations/start-sit` 177ms → 4.6ms avg, `GET /players/{id}` 25.5ms → 3.6ms avg), Bucket4j rate limiting on `/api/auth/**`, Bean Validation on previously-unconstrained request params.

**Phase 5, first slice** — trade analyzer (`POST /api/trades/analyze`): rest-of-season value comparison reusing three of the six factor calculators plus a positional replacement-level adjustment. ~3.3s per request (known, deliberately-accepted N+1 cost), not batched yet.

**Phase 6, first slice** — Micrometer instrumentation for ingestion job duration/success (`ingestion.run.duration`, tagged by source/outcome). Request latency, cache hit ratio, and external API error rate turned out to already be auto-instrumented for free — confirmed live rather than assumed.

Full writeups for all of the above in `CLAUDE.md`.

## Phase 7 — AWS deployment & CI/CD (PRs #18-25, all merged)

Setup done first: AWS CLI + Terraform installed locally, an IAM user (`fantasyiq-terraform`, `AdministratorAccess` for now) created for Terraform to use, a billing alert set up in AWS Budgets. Account is on AWS's credit-based Free Plan (6 months or until credits run out), which is *why* the build-and-destroy rhythm matters here. Region: `us-east-1`. Budget posture: minimal-cost (single-AZ, no NAT gateway, smallest reasonable instance sizes).

**Dockerfile (PR #18).** Multi-stage build, Gradle running *inside* the container (also sidesteps this dev machine's known Gradle loopback issue). Verified locally against real `docker-compose` Postgres/Redis before ever touching AWS.

**Terraform infrastructure (PRs #19-21).** Network layer (VPC, subnets, no NAT gateway) → security groups + ECR → RDS/ElastiCache/ALB/ECS (the first resources that cost real money). RDS's master password is never set in code — AWS generates and owns it directly in Secrets Manager. A real Terraform-generated `JWT_SECRET` plus empty placeholder secrets for the two external API keys, set by hand via `aws secretsmanager put-secret-value`, never through this code or chat.

**GitHub Actions deploy pipeline (PR #22).** On every push to `main`: build the image, push to ECR tagged with the commit SHA, fetch the live ECS task definition and patch only the image (deliberately not a static file checked into the repo — Terraform already owns the task definition's full shape), redeploy, wait for ECS to confirm healthy. Authenticates via GitHub's OIDC provider — no long-lived AWS keys stored anywhere.

**Two real bugs found only by testing this against a real AWS account, both fixed (PRs #23-25):**
1. **Secrets Manager's 30-day recovery window** conflicts directly with destroying and recreating every session — a deleted secret's name stays reserved for 30 days, so the next `apply` fails trying to recreate it. Fixed with `recovery_window_in_days = 0` on all three custom secrets.
2. **The OIDC trust policy's `sub` condition used the plain `repo:OWNER/NAME:ref:...` format every tutorial (including GitHub's own docs) shows — but this repo's real tokens use GitHub's newer "immutable subject claims" format** (`repo:OWNER@OWNER_ID/NAME@REPO_ID:ref:...`), which embeds numeric ids so the trust policy survives a repo/account rename. Found by decoding a real token live (a temporary debug step), not by assuming documentation was current — the same "verify the real behavior, don't trust the documented format" lesson already learned once this project in a completely different context (`ConstraintViolationException` vs. `HandlerMethodValidationException`, Phase 4).

Full design rationale for all of Phase 7, including the complete OIDC debugging story, in `CLAUDE.md`'s "Phase 7 — AWS deployment & CI/CD" section.

**Verified live, twice, then torn down both times.** `terraform apply` → `/actuator/health` returns `UP` through the ALB's public DNS (which already confirms RDS/ElastiCache connectivity via Spring Boot's aggregated health check) → a real register call confirms the JWT secret wiring → `terraform destroy`. The second time, a real `git push` through the merged pipeline rebuilt and redeployed the app automatically, confirmed via the same health check, before destroying again.

## What remains (lower priority, not blocking)

- The CloudWatch-dashboard/alarms half of Phase 6 — genuinely doable for the first time now that Phase 7's infrastructure exists, but needs the app actually running continuously to have anything to watch
- Trade analyzer performance (N+1 query pattern in `computeReplacementLevels`, ~3.3s per request) — not urgent, occasional endpoint
- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one)
- Backtest performance (N+1 query pattern inside `gatherFactors`, ~18 min for a full season) — not urgent, occasional endpoint
- `MatchupFactorCalculator`'s long-run uniform averaging and the weak `USAGE` factor remain real, un-investigated hypotheses if further model improvement is wanted later

## Recommended next steps

Phase 7 is done. Three real directions from here, none started yet:

1. **Finish Phase 6** — CloudWatch dashboard + alarms (job failure, elevated error rate, circuit breaker trips) + a runbook doc, now that there's real infrastructure to point them at.
2. **Batch the trade analyzer's replacement-level computation** if the 3.3s cost turns out to matter in practice.
3. **Phase 8** — frontend, lower priority per the dev plan, likely follows Phase 7.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, `phase-3/start-sit-scoring-engine`, `phase-3/player-trending-endpoint`, and `phase-3/backtest-validation` still exist on origin from prior slices (deferred cleanup, unchanged). All Phase 7 branches have been deleted both locally and remotely.

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified. **No AWS resources are currently running** — expected, not a problem; it's the intended state between sessions given the credit-limited account. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas").
