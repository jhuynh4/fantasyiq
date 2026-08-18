# Current Work

## Status: Phase 4 first slice (Redis caching) merged — no active branch

`phase-4/redis-caching` (k6 baseline + Redis cache-aside layer for start-sit recommendations and player detail) is merged to `main` (PR #14) and the local branch is cleaned up. Everything described below is on `main`.

## What's been completed (Phases 1-3, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2** — games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle, `defense_vs_position_stats`, `weather_forecasts`, `betting_lines`.

**Phase 3** — start/sit recommendation engine (6 factors), player trending endpoint, backtest validation, weight tuning + recent-performance factor (took real predictive correlation from `0.0137` to `0.306`). Full writeup in `CLAUDE.md`.

## Phase 4, first slice: Redis caching (PR #14, merged)

### Caching layer

- `RedisCacheConfig`, `RecommendationCacheService`, `PlayerCacheService` (new `cache` package) — cache-aside, populated both by the write side right after it commits (`StartSitRecommendationService.computeForWeek` → `refreshStartSit`, `PlayerIngestionService` → `PlayerCacheService.refresh` per athlete) and, as a fallback, by the read side on a genuine cache miss.
- `RecommendationSnapshot`/`PlayerSnapshot` (new domain records) — flat, cache-friendly values shared between the write side (analytics/ingestion, which can't depend on `api.dto`) and the read side, instead of caching JPA entities or api DTOs directly.
- `RecommendationController`/`PlayerController` read through the cache now; response DTOs adapted to the snapshot types; one dead repository query method removed (position filtering moved to an in-memory post-cache-read filter).
- IT wiring: `IntegrationTestBase` now also starts a shared Redis Testcontainer, same pattern as Postgres, plus a per-test cache flush. New `RecommendationCacheServiceIT`/`PlayerCacheServiceIT`, plus one new test each in `StartSitRecommendationServiceIT`/`PlayerIngestionServiceIT` proving the write side actually populates the cache.

### Five real bugs found and fixed (three live, two CI-only)

Manual testing against the live app plus real CI runs caught bugs invisible to compilation and to any test that mocks the cache layer — a `@Cacheable`/`@CachePut` round-trip against a real serializer is exactly what neither catches without a live Redis:

1. Default `GenericJackson2JsonRedisSerializer` has no `jackson-datatype-jsr310` registered — `PlayerSnapshot.birthDate` (a `LocalDate`) threw on write.
2. Caching `Optional<PlayerSnapshot>` directly is a known Jackson/Redis footgun — fixed by returning a nullable `PlayerSnapshot` instead.
3. The custom `ObjectMapper` used `DefaultTyping.NON_FINAL` instead of `EVERYTHING` — since `PlayerSnapshot`/`RecommendationSnapshot` are Java `record`s (implicitly `final`), `NON_FINAL` silently omitted the `@class` type id on write, causing `InvalidTypeIdException` on the next read.
4. `disableCachingNullValues()` doesn't silently skip a null cache write — it throws `IllegalArgumentException`. Needed `unless = "#result == null"` on `getById` so the cache `put` is never attempted for a not-found lookup. This was a real live bug too: `GET /players/{nonexistent-id}` 500'd on the running app before the fix.
5. A new IT test deleted a player row directly to prove cache-aside behavior, but ingestion had also created a `player_external_ids` row referencing it (FK violation) — same failure mode as manually deleting a reconciled player via `psql` during live testing. Fixed by deleting `player_external_ids` first.

Full root-cause writeup in `CLAUDE.md`'s "Caching" section, including exactly how each was diagnosed (direct `redis-cli`/`psql` checks against the live containers, not guessing).

### Real result — verified live and via a full k6 re-run

Confirmed live: a direct `UPDATE players SET status = ...` in Postgres (bypassing the app) followed by a `GET /players/{id}` still returned the pre-mutation cached value — proof the cache is genuinely being read, not silently falling through to the DB every time.

k6 re-run post-caching (`docs/perf/after-caching.md`/`.json`, same fixed keys, same load shape as `docs/perf/baseline.md`/`.json`):

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

Two real directions from here, neither started yet:

1. **Finish Phase 4** — Bucket4j rate limiting, broader Bean Validation on request DTOs.
2. **Move to Phase 5** — waiver-wire/trade analysis, which reuses the same factor engine now sitting behind a validated cache.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, `phase-3/start-sit-scoring-engine`, `phase-3/player-trending-endpoint`, and `phase-3/backtest-validation` still exist on origin from prior slices (deferred cleanup, unchanged). `phase-4/redis-caching` has been deleted both locally and (via the GitHub merge) remotely.

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified, including the new Redis Testcontainers wiring now exercised in CI. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas").
