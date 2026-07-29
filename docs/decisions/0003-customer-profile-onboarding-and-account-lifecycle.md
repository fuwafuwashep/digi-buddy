# ADR 0003: Customer profile, onboarding, and account lifecycle

- **Status:** Accepted
- **Date:** 2026-07-16

## Context

Phone authentication now creates the canonical user identity. Digibuddy needs a simple, skippable customer onboarding flow, private profile/settings data, profile photos, privacy requests, and in-app account deletion without duplicating identity or exposing phone numbers to helpers.

## Decision

1. `user_identity` remains the account and verified-phone authority. `customer_profile` is a one-to-one customer projection keyed by the same UUID; that UUID is also the customer ID.
2. A new `:shared:profile` feature module owns onboarding/profile/settings presentation and use cases. It depends on contracts/networking and does not own authentication tokens.
3. The backend alone returns the verified phone number to the authenticated customer profile endpoint. No public/helper DTO contains a phone field.
4. Onboarding requires only first name, last name, and a US ZIP code. Photo, location permission, notification permission, and technology preferences are explicitly skippable. Email/password is excluded.
5. Profile photo bytes never pass through JSON. The backend validates declared type and size before issuing an upload grant and validates uploaded bytes again. Local development uses a bounded in-memory upload adapter. Hosted object storage uses a short-lived presigned `PUT` interface; provider credentials stay server-side.
6. Accessibility preferences are account settings synchronized by the backend. System text size remains the default; extra-large text, high contrast, reduced motion, and simplified instructions are opt-in overrides.
7. Data-export and deletion actions create auditable server-side requests. Deletion requires a newly phone-authenticated session no more than ten minutes old and revokes every session when accepted. A stale session must sign in with SMS again. The account enters `DELETION_REQUESTED` rather than being immediately hard-deleted because retention, fraud, dispute, and legal requirements remain undecided.
8. Active bookings do not exist yet. The deletion service exposes an explicit booking-blocker port that currently reports no active bookings. The bookings phase must implement that port and define cancellation/retention behavior before bookings launch.
9. Biometric unlock is only a local convenience for releasing an already stored refresh token on a trusted device. It never substitutes for backend authentication or fresh SMS verification for destructive account actions.

## Consequences

- Customer profile reads are private by default and require the customer's access token.
- Public helper-facing profile contracts, if introduced, must be separate allowlisted DTOs.
- Presigned URLs are bearer capabilities and must be short-lived, single-purpose, and excluded from logs.
- Soft deletion preserves an auditable workflow but requires a later retention/erasure policy and background processor.
- Platform photo pickers, camera permission, notification permission, location permission, and biometric prompts remain platform adapters around shared state.
