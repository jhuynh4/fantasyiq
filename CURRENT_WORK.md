# Current Work

## Status: Redis caching (Phase 4, first slice) built and verified live — ready to push for CI

Branch `phase-4/redis-caching` adds a k6 performance baseline plus a Redis cache-aside layer for the two most expensive hot read paths. Built clean via IntelliJ and manually verified against the real running app + real Redis (not just the IT suite) — including three real bugs found and fixed only once tested against a live Redis instance. Not yet committed beyond the baseline; see "What's on this branch" below.

## What's been completed (Phases 1-3, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2** — games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle, `defense_vs_position_stats`, `weather_forecasts`, `betting_lines`.

**Phase 3** — start/sit recommendation engine (6 factors), player trending endpoint, backtest validation, weight tuning + recent-performance factor (took real predictive correlation from `0.0137` to `0.306`). Full writeup in `CLAUDE.md`.

## Phase 4, first slice: Redis caching (branch `phase-4/redis-caching`)

### Baseline (committed as `1dea4ed`)

`scripts/perf/hot-read-paths.js` (k6) hammers a small, fixed set of hot keys at 20 VUs for a minute. Pre-caching baseline saved to `docs/perf/baseline.md`/`.json`.

### Caching layer (built, verified live, not yet committed)

- `RedisCacheConfig`, `RecommendationCacheService`, `PlayerCacheService` (new `cache` package) — cache-aside, populated both by the write side right after it commits (`StartSitRecommendationService.computeForWeek` → `refreshStartSit`, `PlayerIngestionService` → `PlayerCacheService.refresh` per athlete) and, as a fallback, by the read side on a genuine cache miss.
- `RecommendationSnapshot`/`PlayerSnapshot` (new domain records) — flat, cache-friendly values shared between the write side (analytics/ingestion, which can't depend on `api.dto`) and the read side, instead of caching JPA entities or api DTOs directly.
- `RecommendationController`/`PlayerController` read through the cache now; response DTOs adapted to the snapshot types; one dead repository query method removed (position filtering moved to an in-memory post-cache-read filter).
- IT wiring: `IntegrationTestBase` now also starts a shared Redis Testcontainer, same pattern as Postgres, plus a per-test cache flush. New `RecommendationCacheServiceIT`/`PlayerCacheServiceIT`, plus one new test each in `StartSitRecommendationServiceIT`/`PlayerIngestionServiceIT` proving the write side actually populates the cache.

### Three real bugs, found only once tested against a real Redis instance

Manual testing against the live app (not just IT tests) caught three real, sequential bugs in the Redis Jackson serializer config — each one only surfaced with a genuine write-then-read round-trip against real Redis:

1. Default `GenericJackson2JsonRedisSerializer` has no `jackson-datatype-jsr310` registered — `PlayerSnapshot.birthDate` (a `LocalDate`) threw on write.
2. `PlayerCacheService.getById` originally returned `Optional<PlayerSnapshot>` — caching an `Optional` with the Jackson Redis serializer is a known footgun. Fixed by returning a nullable `PlayerSnapshot` and relying on `disableCachingNullValues()`.
3. The custom `ObjectMapper` used `DefaultTyping.NON_FINAL` instead of `EVERYTHING` — since `PlayerSnapshot`/`RecommendationSnapshot` are Java `record`s (implicitly `final`), `NON_FINAL` silently omitted the `@class` type id on write, causing `InvalidTypeIdException` on the very next read.

Full root-cause writeup in `CLAUDE.md`'s "Caching" section, including exactly how each was diagnosed (direct `redis-cli`/`psql` checks against the live containers, not guessing).

### Real result — verified live and via a full k6 re-run

Confirmed live: a direct `UPDATE players SET status = ...` in Postgres (bypassing the app) followed by a `GET /players/{id}` still returned the pre-mutation cached value — proof the cache is genuinely being read, not silently falling through to the DB every time.

Re-ran the k6 baseline post-caching (`docs/perf/after-caching.md`/`.json`, same fixed keys, same load shape):

| Endpoint | avg before | avg after | p95 before | p95 after |
|---|---|---|---|---|
| `GET /recommendations/start-sit` (all) | 177.2ms | 4.6ms | 306.2ms | 8.1ms |
| `GET /recommendations/start-sit?position=WR` | 136.5ms | 4.6ms | 238.8ms | 8.5ms |
| `GET /players/{id}` | 25.5ms | 3.6ms | 66.2ms | 6.8ms |
| `GET /players/{id}/trending` (uncached) | 20.1ms | 7.5ms | 59.7ms | 12.5ms |

Total requests completed in the same 80s window: 8,507 → 17,475. The two cached endpoints improved ~30-38x on average latency; the *uncached* trending endpoint also got faster (20ms → 7.5ms) purely from reduced DB contention — a real secondary benefit.

## What remains (lower priority, not blocking)

- Rest of the Phase 4 checklist: Bucket4j rate limiting, Bean Validation on request DTOs beyond what already exists
- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one)
- Backtest performance (N+1 query pattern inside `gatherFactors`, ~18 min for a full season) — not urgent, occasional endpoint
- `MatchupFactorCalculator`'s long-run uniform averaging and the weak `USAGE` factor remain real, un-investigated hypotheses if further model improvement is wanted later

## Recommended next steps

1. Commit the caching layer (currently uncommitted on `phase-4/redis-caching`, verified live) and push, let CI verify — the new Testcontainers Redis wiring in `IntegrationTestBase` hasn't been exercised in CI yet, only locally against a real Redis container.
2. Once merged: either continue Phase 4 (rate limiting, validation) or move to waiver/trade (Phase 5), which reuses the same factor engine now sitting behind a validated cache.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, `phase-3/start-sit-scoring-engine`, `phase-3/player-trending-endpoint`, and `phase-3/backtest-validation` still exist on origin from prior slices (deferred cleanup, unchanged).

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified. `phase-4/redis-caching` is verified live against the real app but not yet through CI — the new Redis Testcontainers wiring is untested in that environment. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas").
