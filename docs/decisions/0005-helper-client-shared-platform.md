# ADR 0005: Helper client on the shared platform

**Status:** Accepted

**Date:** 2026-07-18

## Context

Digibuddy Helpers needs a separate product experience without creating a second backend, identity system, database, or incompatible copies of booking, chat, and payment state. AGP 9 also requires a thin Android application host separate from a KMP shared application module.

## Decision

Add `:apps:helpers` as the helper KMP/Compose application and `:apps:helpers:androidApp` as its Android host. Add a native SwiftUI/Xcode host under `apps/helpers/iosApp`. Helper onboarding and dashboard presentation live in focused shared KMP modules and consume the existing contracts, authentication, networking, and design system.

Add one authenticated, additive `/api/v1/helper/startup` read endpoint. It uses the existing user identity roles and the existing `helper_profile` account/approval fields. Accounts without the helper role route to onboarding; helper-role accounts route according to the server-owned catalog lifecycle. Existing customer routes and DTO behavior do not change.

## Consequences

- Both apps authenticate against the same sessions and identities, but keep platform-secure refresh-token storage in their own application sandbox.
- Booking, chat, and payment contracts remain canonical in `:shared:contracts` and are not duplicated.
- The helper shell may display polished placeholders during H0, but future commands must use helper-authorized endpoints and the existing server state machines.
- PostgreSQL remains the single hosted database. No migration is required for H0 because the startup read uses V2 roles and V4 helper-profile fields.
- Native iOS linking and signing still require macOS/Xcode verification.
