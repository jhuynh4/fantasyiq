# Runbook: Injury ingestion job failure

One real runbook, written end to end, as a template for any other ingestion job in this project — the failure mode, the diagnosis steps, and the fix are structurally identical for `players`/`games`/`player-game-stats` ingestion too, since they all go through the same `IngestionRunService.track(...)` wrapper (see `CLAUDE.md`'s "Ingestion architecture").

## What this job does

`POST /api/injuries/ingest` (`InjuryIngestionService`, source `INJURY_PROVIDER`) pulls injury reports from ESPN and reconciles them into `injury_reports`. It also runs automatically once a day via `IngestionScheduler`'s cron. Player ingestion must have already run at least once — injury reports for a player ESPN doesn't know about yet are silently skipped, not an error.

## How you'd actually notice this failed today

**Be honest about this: there is no dedicated alert for this yet.** The alarms this project currently has (`infra/alerts.tf`) watch ALB/ECS/RDS infrastructure health, not individual ingestion job outcomes — a failed injury-ingestion run doesn't crash the app or fail a health check, so it won't trip any of those. Until the app-level metrics bridge (see `CLAUDE.md`'s "Phase 6" section, "deferred") exists, the honest way to notice this is to check for it manually:

1. **Query the audit table directly.**
   ```sql
   select * from ingestion_runs
   where source = 'INJURY_PROVIDER'
   order by started_at desc
   limit 5;
   ```
   A `status = 'FAILED'` row has `error_message` populated and a `correlation_id` — that id is the key to everything else below.

2. **If you triggered it manually** (`POST /api/injuries/ingest`), the response itself tells you: a `503` with a body like `{"detail": "ESPN injuries endpoint unavailable"}` means the circuit breaker tripped or the call genuinely failed (see "Common causes" below). Any other 5xx is unexpected — go straight to the logs.

## Diagnosis steps

1. **Get the correlation id** from the failed `ingestion_runs` row (or from the `503` response if you triggered it manually and the detail message doesn't explain enough).
2. **Find the exact log lines in CloudWatch** — log group `/ecs/fantasyiq`, every log line from that run carries the same correlation id in its structured JSON (see `logback-spring.xml`). In the CloudWatch console: Logs Insights, then
   ```
   fields @timestamp, @message
   | filter correlationId = "<the id from step 1>"
   | sort @timestamp asc
   ```
   This reconstructs the exact sequence of what happened during that run, not just the final error.
3. **Check the circuit breaker's current state** — if ESPN was genuinely down or rate-limiting, the `espnApi` circuit breaker (shared by all ESPN-backed ingestion: players, games, player-game-stats, and injuries) may have tripped OPEN, meaning *every* ESPN-backed job will keep failing fast until it recovers, not just this one. `/actuator/prometheus` (authenticated) exposes `resilience4j_circuitbreaker_state{name="espnApi",...}` — a live, real-time view of this, though it's not yet on the CloudWatch dashboard (same deferred app-metrics gap noted above).

## Common causes, and what to do about each

- **ESPN's Akamai edge blocked the request** (see `CLAUDE.md`'s "Hard-won ESPN quirks") — this manifests as the circuit breaker tripping after a handful of failures, surfacing as `EspnUnavailableException`/`503`. Nothing to fix on this project's side; retrying later (next day's scheduled run, or a manual re-trigger) usually succeeds once ESPN's edge stops flagging the traffic.
- **A genuinely new/changed ESPN response shape** — if the error in the logs is a deserialization/mapping exception rather than a timeout or 5xx from ESPN, that's a real bug, not a transient vendor issue. Check `EspnResponseMapper`/the injury-specific DTOs against a fresh real response before assuming a quick retry will fix it.
- **Player ingestion hasn't run yet for a new season** — not actually a failure mode (unresolvable reports are silently skipped, logged but not counted as an error), but if `records_processed` is suspiciously low (e.g. `0`), confirm `POST /api/players/ingest` has run recently.

## After the root cause is fixed

Re-trigger manually: `POST /api/injuries/ingest` (see `api-requests.http`). It's safe to run repeatedly — reconciliation is upsert-based, not append-only, so a retry after a partial failure won't create duplicate rows.
