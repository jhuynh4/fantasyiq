# FantasyIQ — Measuring Your Architecture Decisions

You picked Redis, Postgres, Docker, AWS, retries/circuit breakers, and scheduled jobs because you want the *experience* of using them. But "I used Redis" is a weak resume/interview claim. "I measured a 94% cache hit rate that cut p95 latency from 340ms to 22ms" is a strong one — and more importantly, it's the actual engineering discipline of validating a decision instead of just making it. This doc gives you a concrete method for each major choice: what to measure, how to measure it, and what tooling to use.

---

## 1. The Practice: Architecture Decision Records (ADRs)

Before benchmarking anything, adopt a lightweight habit: every non-trivial technical decision gets a short written record — not after the fact for a resume, but at the moment you make it, so the "why" and the "how would I know if I was wrong" are captured while they're fresh.

**Template** (`docs/adr/0004-redis-caching.md`):
```markdown
# ADR 0004: Cache-aside Redis layer for player profiles and rankings

## Status: Accepted

## Context
Player profile and rankings endpoints are read-heavy relative to write frequency
(data refreshes once/day via ingestion, but reads happen constantly during
active browsing sessions).

## Decision
Cache-aside pattern in Redis, keys invalidated by ingestion/scoring jobs on write,
not by request-driven TTL expiry.

## Alternatives Considered
- No caching, rely on Postgres query performance + indexing alone
- In-memory (Caffeine) local cache instead of Redis
- TTL-based cache instead of write-driven invalidation

## How I'll know this was the right call
- p95 latency on GET /players/{id} and GET /rankings under k6 load, cached vs uncached
- Cache hit ratio in production-like load test (target: >85% for popular players)
- Postgres query count/CPU under the same load, with and without cache

## Result (filled in after measuring)
[link to benchmark results]
```

Do this for every major decision: Redis, retries/circuit breakers, async scheduled jobs vs. synchronous, Docker vs. bare-metal-style local run, ECS Fargate vs. EC2, index choices. **The "how I'll know" section is the important part** — it forces you to define the metric *before* you have a result, which is the actual scientific-method discipline that separates "I chose X" from "I validated X."

---

## 2. Redis Caching — What to Measure

**Claim you want to be able to make:** "Caching reduced read latency by X% and cut database load by Y% under realistic traffic."

**Method:**
1. Build a k6 (or Gatling) load test script that hits `GET /players/{id}` and `GET /rankings` with a realistic access pattern — most fantasy traffic is skewed toward popular players (a Zipfian/power-law distribution, not uniform), so weight your test traffic accordingly (e.g., 80% of requests hit the top 20% of players) rather than testing uniform random IDs, which would understate cache benefit.
2. Run the test **twice**: once against a build with caching disabled (feature-flag it or point at a branch before the Redis layer existed), once with it enabled.
3. Capture: p50/p95/p99 response time, requests/sec sustained, and Postgres connection pool utilization/query count (visible via Micrometer/Actuator metrics or `pg_stat_statements`) during each run.
4. Separately record **cache hit ratio** directly from Redis (`INFO stats` → `keyspace_hits` / `(keyspace_hits + keyspace_misses)`) during the cached run.

**What "good" looks like:** a large latency gap between cached/uncached at p95+ (caching mostly helps tail latency, not average — say so honestly), and a hit ratio in the 80–95% range for a Zipfian access pattern. If your hit ratio is low, that's a real, useful finding too — it tells you your TTL/invalidation strategy or key granularity needs work, which is itself a demonstrable engineering insight ("initial cache key granularity was too fine, hit ratio was only 40%; consolidating per-player keys into a batched rankings key raised it to 88%").

---

## 3. Retry & Circuit Breaker (Resilience4j) — What to Measure

**Claim you want to be able to make:** "The system maintained X% availability during simulated third-party API outages, and the circuit breaker prevented cascading slowdowns."

**Method:**
1. Use WireMock (already in your test stack for contract tests) to simulate a failing external API — configure it to return 500s or hang past your timeout for a controlled window.
2. Run your ingestion job against this failing mock **with** retry/circuit-breaker enabled and **without** (a config flag or a stripped-down comparison branch).
3. Measure: total ingestion job duration during the outage window, number of failed vs. retried vs. successful calls, and — critically — **whether the failure stayed contained** (did other unrelated ingestion jobs/endpoints slow down because of thread pool exhaustion from the failing calls, in the no-circuit-breaker case?).
4. A good test: simulate the external API failing for exactly 2 minutes, then recovering, and measure how long your circuit breaker takes to detect recovery and resume normal calls (this is the "half-open state" behavior — worth explicitly logging and timing).

**What "good" looks like:** without protection, a failing external API causes visibly increasing response times or thread starvation elsewhere in the app (you can literally screenshot this happening); with protection, the circuit opens quickly, fails fast, and the rest of the system stays responsive — a concrete, demonstrable "I prevented a cascading failure" story with numbers attached.

---

## 4. Database Indexing & Query Design — What to Measure

**Claim you want to be able to make:** "Adding a composite index on (week, season, position) reduced the rankings query from Xms to Yms at realistic data volume."

**Method:**
1. Seed your local Postgres with a realistic data volume — not 50 test rows, but something like a full season's worth of `player_game_stats` (roughly 1,700 players × 18 weeks ≈ 30,000 rows minimum; scale up further to stress-test, e.g., 3 seasons ≈ 90,000 rows) so query plans reflect real-world scale, not toy data where every query is fast regardless of indexing.
2. Run your actual rankings/query with `EXPLAIN ANALYZE` before adding the relevant index — capture the plan (sequential scan vs. index scan) and actual execution time.
3. Add the index, re-run `EXPLAIN ANALYZE`, capture the new plan and time.
4. Do this for at least 2–3 of your most-hit queries (rankings, player game log, recommendation lookups) — a table of before/after `EXPLAIN ANALYZE` output is genuinely compelling, concrete evidence and easy to include directly in a README or blog post.

**What "good" looks like:** a clear shift from `Seq Scan` to `Index Scan` (or `Bitmap Index Scan`) in the plan, with a measurable execution time drop — often dramatic (10x+) once data volume is realistic, which is exactly the point: this is the kind of measurable that's invisible at toy scale and only shows up when you deliberately test at production-like volume.

---

## 5. Scheduled/Async Jobs vs. Synchronous — What to Measure

**Claim you want to be able to make:** "Moving ingestion off the request path let the API stay responsive during data refreshes, verified under concurrent load."

**Method:**
1. Run a k6 load test against your read endpoints **while** an ingestion job is actively running in the background (scheduled or manually triggered mid-test).
2. Measure whether read-endpoint latency/error rate is affected by the concurrent ingestion job — it shouldn't be, if scheduled jobs are properly isolated (separate thread pool, connection pool sizing that doesn't starve the API's pool).
3. If you want a genuinely interesting comparison: deliberately misconfigure it first (e.g., ingestion sharing the same small connection pool as the API) and show degraded read latency during ingestion, then fix the isolation and show the improvement. This "before/after a real bug" story is more convincing than a synthetic best-case test.

---

## 6. Docker / Containerization — What to Measure

This one is less about performance and more about **reproducibility and onboarding time**, which is a legitimate, measurable engineering value even though it's not a latency number.

**Claim you want to be able to make:** "A new environment can be fully running from clone to first successful API response in under N minutes, with zero manual setup steps."

**Method:** literally time it. Clone the repo fresh (or have someone else try), run `docker compose up`, and time to first successful health-check response. Document this number in your README. It's a real, honest metric that matters a lot in actual engineering teams (onboarding cost, CI environment parity) even though it doesn't look like a typical "performance benchmark."

---

## 7. AWS Deployment Choices — What to Measure

**Claim you want to be able to make:** "The system sustains X req/sec at Y ms p95 on a t4g.micro-class deployment, and here's what it costs to run."

**Method:**
1. Once deployed (even in a time-boxed Path B session from the free-tier guide), run the same k6 load test against the live AWS deployment that you ran locally.
2. Compare local Docker Compose performance vs. deployed performance — differences are informative (network latency, real vs. local Postgres/Redis behavior, container resource limits).
3. Record actual AWS Cost Explorer numbers for your time-boxed session (even a few dollars is a real, citable number: "a demo session — provisioning through teardown — cost $1.40").
4. If you scale the ECS task count or ElastiCache size, redo the load test and show the scaling relationship (e.g., doubling Fargate tasks roughly doubled sustained throughput up to the point Postgres connections became the bottleneck) — this kind of "found the actual bottleneck" story is far more valuable than an unqualified "it scales."

---

## 8. Tooling Summary

| Purpose | Tool |
|---|---|
| HTTP load testing | **k6** (scriptable in JS, good CLI output, free) or Gatling (Java-native, fits your stack) |
| Application metrics | **Micrometer** + Spring Boot Actuator, scraped by Prometheus, visualized in Grafana (all free, runs in Docker Compose locally) |
| Database query analysis | `EXPLAIN ANALYZE`, `pg_stat_statements` extension |
| Chaos/failure simulation | **WireMock** (delays, fault injection, canned error responses) — you already need this for contract tests, so it's dual-purpose |
| Microbenchmarking pure logic (e.g., scoring engine factor calculators) | **JMH** (Java Microbenchmark Harness) if you want nanosecond-level rigor on hot-path scoring code — optional, more relevant if you want to demonstrate low-level performance tuning skill specifically |
| Cache stats | Redis `INFO stats`, or expose hit/miss counters through Micrometer for a unified dashboard alongside app metrics |

---

## 9. How to Present This (Making the Measurables Count)

- **A `BENCHMARKS.md` or a "Performance" section in your README** with a short table per decision: what was measured, method, before/after numbers, and a one-line takeaway. This is the artifact a recruiter or interviewer actually reads.
- **Keep the ADRs in `docs/adr/`** in the repo itself — this is a real practice used at serious engineering orgs, and having actual dated ADRs with filled-in "Result" sections is a strong, unusual signal for a solo project.
- **Screenshot Grafana dashboards** during a deliberate load test and check the images into the repo (or a `docs/` folder) — a visual of p95 latency dropping after a change is more persuasive than a sentence claiming it.
- **Be honest about neutral or negative results.** If a decision didn't measurably help (e.g., caching a rarely-hit endpoint that gets a low hit ratio), document that too — "I measured X, found the benefit was smaller than expected because Y, so I [adjusted / kept it anyway for Z reason]" is a *more* credible engineering story than every decision magically paying off, and it demonstrates the actual skill (measuring and reasoning) rather than just picking popular tech and asserting it worked.

---

## Next Steps

A good next step is picking one decision — Redis caching is the easiest first target since it has the clearest before/after story — and actually writing the k6 script and ADR together before you've even built much else, so the measurement habit is in place from early Phase 1/4 rather than retrofitted at the end.
