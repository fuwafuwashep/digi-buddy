# Digibuddy Development Plan

**Planning date:** 2026-07-16

**Delivery posture:** iOS first, Android target retained, one backend for both customer and helper apps

Each phase must update `docs/PROJECT_STATUS.md` with commands, actual test outcomes, incomplete work, risks, and the next exact step. A phase is not complete until its exit criteria are met.

## Phase 0 — inspection and architecture preparation

Deliverables:

- Inventory and preserve the source logo
- Inspect Git and local tooling
- Establish repository and security rules
- Select a stable compatible technology baseline
- Document architecture, status, plan, and security boundaries
- Create the empty repository skeleton

Exit criteria:

- Required files exist and are internally consistent
- Logo hash matches the recorded baseline after all work
- Git repository passes an integrity check
- No feature implementation or secret material exists

## Phase 1 — build and module scaffolding

Scope:

1. Add the Gradle 9.5.0 Wrapper and checksum verification.
2. Add `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`, and `gradle/libs.versions.toml` with the accepted versions.
3. Register `:apps:customer`, all shared modules including `:shared:contracts`, and `:backend`.
4. Configure Kotlin/KMP source sets for iOS device, Apple-silicon simulator, and Android.
5. Add only minimal compile/smoke code and test fixtures needed to verify module wiring.
6. Add the thin Xcode iOS shell after the minimum deployment target is confirmed.
7. Add CI jobs for Windows/Linux JVM and Android checks plus macOS iOS checks.

Explicitly excluded: login, signup, profiles, booking flows, chat, payment flows, production endpoints, and real secrets.

Exit criteria:

- Clean dependency resolution succeeds from the wrapper
- `projects`, common tests, backend tests, and Android compilation run successfully
- iOS simulator framework/app compilation runs successfully on macOS with Xcode 26.4
- Dependency locking/verification and version catalog are committed
- Test commands and results are recorded in project status

## Phase 2 — shared foundations and backend skeleton

Scope:

- Implement common IDs, time abstractions, error types, result conventions, and serialization rules
- Define API error envelope and versioning through a recorded decision
- Build accessible design tokens and primitive Compose components
- Configure Ktor client engines and a mock transport
- Configure SQLDelight schemas/migrations for non-sensitive cache data
- Establish Koin composition roots and constructor-injected services
- Create Ktor server health/readiness endpoints, structured configuration, error handling, and test harness
- Select and record PostgreSQL access and migration libraries

Exit criteria:

- All shared modules compile for iOS and Android
- Backend starts in a test environment and health checks pass
- Serialization contract and migration tests pass
- Accessibility primitives have semantics and scaling tests
- No production credentials are required

## Phase 3 — identity and profiles

**Status:** Customer authentication and the customer onboarding/profile foundation are implemented. Hosted profile persistence, platform photo/permission/biometric adapters, and the cross-host verification gates in `docs/PROJECT_STATUS.md` remain before this phase is production-ready.

Only begin after choosing an identity approach and documenting token lifecycle, account recovery, verification, roles, and privacy requirements.

Build authentication server-side first, with customer/helper roles and platform secure token storage. Authorization must be resource- and action-specific. Add abuse controls, audit events, revocation, and integration tests. Do not use mobile-embedded secrets.

## Phase 4 — marketplace discovery and availability

**Status:** The backend helper catalog, fictional seeds, eligibility policy, ZIP/radius/remote matching, filters, sorting, pagination, and read endpoints are implemented. PostgreSQL/PostGIS migration execution remains blocked on the current host because Docker and PostgreSQL tools are unavailable.

Introduce helper profiles, service categories, service areas, search, pricing display, and availability reads. Keep matching logic and eligibility authoritative on the backend. Prioritize clear language, large touch targets, Dynamic Type/font scaling, screen-reader labels, and non-color status cues.

The H0 Helpers shell and H1 authentication/onboarding lifecycle are implemented at `apps/helpers`. H1 persists independent steps, review status, required changes, and approval events in the existing database; approval grants the shared identity's helper role. Later helper workflow phases must extend these boundaries rather than creating alternate identity, domain, or persistence models.

## Phase 5 — bookings

Define the booking state machine in an ADR before code. Expected concepts include draft/requested, offered/accepted, confirmed, in-progress, completed, canceled, disputed, and expired, but exact states require product and operations input.

All transitions occur on the server with authorization, transition guards, optimistic concurrency/versioning, idempotency, audit history, and notification outbox behavior. Mobile apps request transitions and reconcile returned state; they never finalize booking state locally.

## Phase 6 — chat and notifications

Add conversation authorization based on booking/role policy, persisted server sequence IDs, pagination, delivery/read state, abuse reporting, retention policy, and push-notification indirection. WebSockets improve immediacy but HTTP synchronization remains the recovery path. Never put message contents or secrets in push payloads or ordinary logs.

## Phase 7 — payments

Choose a marketplace-capable payment provider and complete legal/compliance review before implementation. Use provider-hosted/tokenized collection, signed webhooks, idempotency keys, immutable ledger-style records, reconciliation, refund/dispute handling, and server-authoritative state.

Raw card data must never reach Digibuddy clients, logs, local databases, or backend storage. Clients may use provider publishable identifiers but never API secrets.

## Phase 8 — hardening and launch readiness

- Threat-model review and penetration testing
- Dependency/SBOM, secret, static-analysis, and container scanning
- Load, resilience, backup/restore, disaster recovery, and reconciliation tests
- Accessibility audit with assistive technologies and representative users
- Privacy/retention enforcement and support/admin access review
- App Store signing, privacy manifests, review assets, and operational runbooks
- Gradual rollout, alerting, incident response, and rollback exercises

## Immediate next action

For H2, add the helper public-profile editor on top of the H1 application/public-snapshot boundary. Implement validated profile/banner uploads, development storage and presigned-upload adapters, moderation state, and a customer-compatible preview model. Do not expose private H1 payloads or make rating, review, verification, approval, response-time, or ranking fields editable.

In parallel on capable hosts, run Flyway V10 against PostgreSQL/PostGIS with Docker and build both iOS hosts in Xcode. Record the results in `docs/PROJECT_STATUS.md`; Windows source compilation does not replace those runtime checks.
