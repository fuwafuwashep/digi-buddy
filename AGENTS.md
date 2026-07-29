# Digibuddy Engineering Rules

This file applies to the entire repository. More specific `AGENTS.md` files may add rules for a subtree, but may not weaken these rules without an explicit, recorded architecture decision.

## Permanent rules

1. Preserve existing working code. Make the smallest safe change and do not discard unrelated work.
2. Preserve `DigibuddyLogo.png` as the original source logo. Never delete, rename, overwrite, recompress, or otherwise alter it. Derived assets must use different file names.
3. Never claim a test passed unless it was actually run. Record skipped, unavailable, and failed checks honestly.
4. Never commit secrets. This includes credentials, private keys, signing material, production data, and populated environment files.
5. Never put API secrets in a mobile app. Mobile binaries and devices are untrusted; only public identifiers may be embedded.
6. Keep customer and helper apps connected to one backend. They must use one canonical domain model.
7. Use server-authoritative booking and payment states. Clients may request transitions but must never declare them final.
8. Prioritize accessibility and simple language in product design, copy, UI behavior, tests, and reviews.
9. Update `docs/PROJECT_STATUS.md` after every phase, including scope completed, outstanding work, risks, and the next exact step.
10. Record commands executed and test results in `docs/PROJECT_STATUS.md`. Include failures and environmental blockers.
11. Do not silently replace major architecture decisions. Document the proposed change, rationale, consequences, and migration path before implementation; use an ADR under `docs/decisions/` when the decision is material.

## Architecture guardrails

- Build an iOS-first Kotlin Multiplatform client while retaining an Android target that stays compilable.
- Keep shared business rules in focused modules; platform code should be limited to platform APIs, composition roots, and native integration.
- Use a single Ktor backend for both apps. Separate customer and helper permissions through authenticated roles and server-side authorization, not separate backends.
- Treat mobile storage as a cache, not the system of record for identities, bookings, availability, chat delivery, or payments.
- Keep API contracts explicit and versioned. Do not expose persistence entities directly as network contracts.
- Make state transitions idempotent where retries are possible, especially booking, messaging, webhook, and payment operations.
- Add dependencies through the central version catalog once the Gradle build exists. Avoid ad hoc versions in module build files.

## Security and data rules

- Store user credentials or refresh tokens only in platform secure storage. Do not store them in plain preferences or an unencrypted local database.
- Enforce authentication, authorization, ownership, and role checks on the server for every protected operation.
- Minimize collection and retention of personal data. Never log tokens, secrets, payment details, or sensitive message contents.
- Use a payment provider's tokenized client flow when payments are implemented; raw card data must not reach Digibuddy clients or servers.
- Verify webhook signatures and use idempotency keys before applying external state changes.

## Delivery discipline

- Read `docs/ARCHITECTURE.md`, `docs/SECURITY_MODEL.md`, and `docs/PROJECT_STATUS.md` before changing architecture or starting a new phase.
- Keep authentication, bookings, chat, and payments out of the initial build-scaffolding phase.
- Add tests with implementation, run the relevant checks, and record the exact commands and outcomes.
- Do not mark a phase complete while its declared exit criteria are unmet.
