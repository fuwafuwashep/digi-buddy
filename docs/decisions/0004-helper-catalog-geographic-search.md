# ADR 0004: Helper catalog and geographic search

- **Status:** Accepted
- **Date:** 2026-07-16

## Context

Customers need to discover eligible fictional helper profiles before the Helpers app exists. Search must support exact ZIP coverage, radius coverage, remote service, filters, deterministic sorting, and pagination without exposing helper private identity data.

## Decision

1. The catalog is a backend bounded context in the existing Ktor service. It exposes customer-safe summary contracts only; helper identity/contact records are never returned.
2. Search requires an authenticated customer session. Every query applies approval, active-account, catalog-visibility, and active-service eligibility before user filters.
3. US ZIP centroids and helper service origins use PostGIS `geography(Point, 4326)`. Exact service ZIP matches are an override; radius matches use `ST_DWithin` in meters and distance display/sorting uses `ST_Distance`.
4. Remote services can match independently of geography. An explicit in-person filter requires exact-ZIP or radius coverage, while an explicit remote filter requires a remote-enabled service.
5. Sorts end with helper UUID as an immutable tie-breaker. Pagination is one-based and capped at 50 results.
6. Ratings, counts, response statistics, and availability are stored summaries, not recomputed from future booking rows. Later booking/review phases must update them transactionally.
7. V4 seed records are invented product fixtures with no phone numbers, emails, street addresses, or other sensitive information. They are marked `seed_data` so hosted deployments can replace them deliberately.

## Consequences

- PostgreSQL search remains indexable and authoritative; the in-memory repository exists only for deterministic unit tests/local fallback.
- ZIP centroid coverage is an approximation suitable for discovery, not routing, travel-time, or address eligibility.
- Recommended ranking is transparent and deterministic but will need a recorded revision before personalization or paid placement.
- The future Helpers app must manage the same catalog tables and approval workflow through authorized backend commands.
