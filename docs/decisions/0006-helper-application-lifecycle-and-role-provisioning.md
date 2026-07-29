# ADR 0006: Helper application lifecycle and role provisioning

- **Status:** Accepted
- **Date:** 2026-07-18

## Context

Digibuddy Helpers must reuse the existing phone identity while keeping private application data separate from both the customer profile and the public helper catalog. Onboarding must survive sign-out and device changes, staff decisions must be auditable, and a mobile client must never be able to approve itself or enable paid work.

The H0 startup contract exposed a small compatibility lifecycle derived from `helper_profile`. H1 requires nine more precise application states and saved progress without breaking existing clients.

## Decision

- One `user_identity` may own a customer profile and one helper application. The helper application is not another login identity.
- Flyway V10 adds `helper_application`, independently saved `helper_application_step` records, application requirements, profile status, approval events, required changes, and a staff-review placeholder to the existing PostgreSQL database.
- The API adds a nullable `helperStatus` to the existing startup response and retains `onboardingStatus` as a compatibility projection. Existing startup fields and customer APIs remain unchanged.
- Step payloads are private, owner-authenticated application data. Public profile output is produced only through an explicit allowlist and never includes legal name, phone number, home ZIP/address, identity documents, bank details, or tax details.
- Only a server-side review transition to `APPROVED` grants the shared `HELPER` role. In PostgreSQL, status approval, approval-event insertion, and role grant share one transaction.
- Paid-work and service-activation authorization requires both the `HELPER` role and application status `APPROVED`. Under-review, changes-requested, paused, suspended, rejected, and incomplete accounts are denied even if a client tampers with local state.
- Existing identity-linked helper profiles are compatibility-backfilled into V10; fictional seed profiles without a user identity are excluded.

## Consequences

- Customer and helper profiles can evolve independently while sharing authentication, sessions, audit conventions, and one backend.
- H2 can build public profile editing on the allowlisted snapshot without exposing private application payloads.
- A real staff/admin authentication and review UI is still required before production; H1 exposes no public self-approval route.
- PostgreSQL migration and transaction behavior must be integration-tested on a Docker/PostGIS-capable host because the current Windows host lacks Docker.

