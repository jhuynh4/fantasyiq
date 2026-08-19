# Current Work

## Status: Phase 4 fully complete and merged — no active branch

Both Phase 4 slices are merged to `main`: Redis caching (PR #14) and rate limiting + request validation (PR #15). Local branches cleaned up. Everything described below is on `main`.

## What's been completed (Phases 1-3, all merged to `main`)

**Phase 1** — auth (JWT register/login/refresh with token rotation) and player ingestion.

**Phase 2** — games/schedule ingestion, injury ingestion, player-game-stats ingestion, hardening bundle, `defense_vs_position_stats`, `weather_forecasts`, `betting_lines`.

**Phase 3** — start/sit recommendation engine (6 factors), player trending endpoint, backtest validation, weight tuning + recent-performance factor (took real predictive correlation from `0.0137` to `0.306`). Full writeup in `CLAUDE.md`.

## Phase 4 — fully complete, all merged

### Slice 1: Redis caching (PR #14)

- `RedisCacheConfig`, `RecommendationCacheService`, `PlayerCacheService` (new `cache` package) — cache-aside, populated both by the write side right after it commits and, as a fallback, by the read side on a genuine cache miss.
- `RecommendationSnapshot`/`PlayerSnapshot` (new domain records) — flat, cache-friendly values shared between the write side (analytics/ingestion, which can't depend on `api.dto`) and the read side.
- Five real bugs found and fixed (three live, two CI-only) — all Redis/Jackson serialization gotchas invisible to compilation: missing `jackson-datatype-jsr310` registration, caching `Optional<T>` directly, `DefaultTyping.NON_FINAL` vs `EVERYTHING` for Java records, `disableCachingNullValues()` throwing instead of silently skipping, and an IT test FK violation. Full root-cause writeup in `CLAUDE.md`'s "Caching" section.
- **Real result**: `GET /recommendations/start-sit` 177ms → 4.6ms avg, `GET /players/{id}` 25.5ms → 3.6ms avg (k6, same fixed hot keys before/after). Total throughput in the same 80s window: 8,507 → 17,475 requests.

### Slice 2: Rate limiting + request validation (PR #15)

- **Bucket4j rate limiting** on `/api/auth/**` only (the only `permitAll` endpoints) — 10 req/min per client IP, in-memory (`ConcurrentHashMap` of `Bucket`s, not Redis-backed — single-instance deployment, no benefit yet to distributing it). Configurable via `RateLimitProperties`; `application-test.yml` relaxes the limit so the IT suite's own auth traffic never trips it.
- **Bean Validation** (`@Min(1)`/`@Max(18)` on `week`, `@Min(1)` on `season`) added to every previously-unconstrained `@RequestParam` across five controllers — `week`'s bound is grounded in the documented regular-season-only scope.
- **A real, previously-invisible bug this surfaced**: `GlobalExceptionHandler` had no handler for `@RequestParam` constraint violations at all — every such violation in the app, including a *pre-existing* `@NotBlank` on `PlayerController.search`'s `q` param (present since an earlier phase, never actually exercised live before now), silently 500'd instead of returning a clean 400. First guess at the exception type (`HandlerMethodValidationException`, Spring MVC's newer native mechanism) was wrong — confirmed via a real stack trace that `@Validated` on a `@RestController` actually routes through Spring Boot's older AOP-based `MethodValidationPostProcessor`, throwing a plain `jakarta.validation.ConstraintViolationException`. Fixed with the correct handler.
- Also fixed a filter-ordering startup crash: `addFilterBefore`/`addFilterAfter` only accept a class from Spring Security's own registered filter ordering as a position anchor, not an arbitrary custom filter like `JwtAuthenticationFilter` — both custom filters now anchor against the same well-known Spring Security class instead.
- All fixes verified live (rate limit tripping at exactly 10 requests in a tight burst, clean 400s on bad params) before being backed by `RateLimitFilterTest` (unit) and `RequestValidationIT` (MockMvc + real Postgres).

Full design rationale for both slices in `CLAUDE.md`'s "Caching" and "Rate limiting & request validation" sections.

## What remains (lower priority, not blocking)

- WireMock contract test coverage for `EspnInjuryProvider` (the only adapter without one)
- Backtest performance (N+1 query pattern inside `gatherFactors`, ~18 min for a full season) — not urgent, occasional endpoint
- `MatchupFactorCalculator`'s long-run uniform averaging and the weak `USAGE` factor remain real, un-investigated hypotheses if further model improvement is wanted later

## Recommended next steps

Every Phase 4 checklist item is closed. Next up: **Phase 5 — waiver-wire/trade analysis**, which reuses the same factor engine now sitting behind a validated cache and rate-limited/validated API surface.

Remote branches `phase-2/defense-vs-position-stats`, `phase-2/weather-forecasts`, `phase-2/betting-lines`, `phase-3/start-sit-scoring-engine`, `phase-3/player-trending-endpoint`, and `phase-3/backtest-validation` still exist on origin from prior slices (deferred cleanup, unchanged). Both Phase 4 branches have been deleted locally and remotely.

## No known blockers or in-flight problems

Everything on `main` is merged and CI-verified, including the new Redis Testcontainers wiring and the rate-limit/validation fixes. Local Gradle CLI has been unreliable this session (JVM loopback-socket issue — worked around by building via IntelliJ instead, see `CLAUDE.md`'s "Local environment gotchas").
