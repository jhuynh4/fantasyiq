# ADR 0001: Modular monolith instead of microservices

## Status
Accepted

## Context
FantasyIQ has clearly separable concerns (ingestion, scoring/analytics, API serving)
that a real engineering team might split into independent services. This is a
solo, part-time project, so team-scaling problems (independent deploys,
independent on-call, org boundaries) don't apply — but the *architectural
discipline* of clean boundaries is still a stated learning goal.

## Decision
Single deployable Spring Boot application, organized feature-first
(`ingestion/`, `domain/`, `analytics/`, `api/`, ...), with module boundaries
enforced by ArchUnit tests rather than by physical service separation.

## Alternatives Considered
- Full microservices (ingestion-service, analytics-service, api-service) —
  rejected: the operational overhead (service discovery, distributed tracing,
  network failure handling between own services) would consume most of the
  time budget without teaching more than the boundaries alone already do.
- Single unstructured Spring Boot app with layer-first packages
  (all controllers together, all services together) — rejected: this doesn't
  enforce or teach clean boundaries at all; it's the "big ball of mud" failure
  mode this ADR is specifically trying to avoid.

## How I'll know this was the right call
- ArchUnit boundary tests (`ArchitectureRulesTest`) stay green as the codebase
  grows past Phase 3 without needing to be loosened or deleted.
- If I ever do want to split a module into a real service later (stretch goal),
  the exercise should take an afternoon, not a rewrite — because the
  dependency direction was already clean.

## Result
Pending — revisit after Phase 3 (analytics module) is built out, since that's
the first point where ingestion → analytics → api boundaries get real traffic
through them.
