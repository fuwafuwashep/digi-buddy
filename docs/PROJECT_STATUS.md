# Digibuddy Project Status

**Last updated:** 2026-07-26

**Current phase:** Real helper workspace, two-party chat, editable helper profiles, and runtime seed removal

**Phase status:** The implemented local customer/helper journeys pass 118/118 connected HTTP checks in ZIP `32539`. The complete customer/helper/shared/backend build, both Android debug APKs, 80 discovered JVM tests, formatting, Detekt, and both iOS simulator Kotlin source targets pass on Windows. Native iOS framework linking and device interaction still require macOS/Xcode.

## Completed work

- Corrected Android Studio import failures under strict Gradle dependency verification. Optional IDE-only `*-sources.jar` and Gradle source archives are narrowly trusted by filename; executable JARs, AARs, modules, POMs, plugins, and other build inputs continue to require recorded SHA-256 checksums.
- Replaced the Helpers Requests, Jobs, and Chats placeholders with authenticated views backed by the existing booking and chat services. Requests includes a visible refresh action plus accept, decline, and message actions.
- Connected a customer's helper-profile **Message** action to a real two-participant conversation. Customer and assigned helper can read and reply in the same thread; the welcome thread remains read-only.
- Added server-side public-helper-ID to private-helper-account resolution and participant authorization. Helper acceptance uses the canonical booking state machine and preserves Digibuddy-owned prices.
- Added full approved-helper profile editing for legal/public names, headline, biography, ZIP, service mode/area, services, skills, experience, languages, availability, certifications, and portfolio links.
- Replaced typed profile-photo URLs with native file selection on JVM, Android, and iOS, backed by validated JPEG/PNG/WebP byte uploads and existing object-storage abstractions.
- Made the default local catalog empty and excluded `seed_data` helpers from public PostgreSQL queries. Fictional helpers remain only as explicitly enabled test fixtures; fake request cards were removed from the Helpers UI.
- Preserved the root logo and recorded the shared booking/chat/profile decision in ADR 0008.

- Added a repeatable connected journey test at `scripts/test-local-journeys.ps1` for ZIP `32539`; it covers both applications through the real local Ktor server rather than isolated service calls alone.
- Added the Crestview `32539` centroid to the deterministic local catalog and verified approved local helpers are discoverable there.
- Fixed local helper catalog synchronization so pausing immediately removes a helper from customer discovery and resuming restores the helper.
- Separated the public catalog helper UUID from the private identity UUID; customer-facing helper responses no longer reuse the account identifier.
- Added exact client-side helper onboarding validation, including the public headline and biography limits that previously appeared only as a backend 400 response. Invalid forms now remain disabled with field-specific guidance.
- Converted helper API failures to friendly application errors instead of displaying raw Ktor request/response exception text.
- Added `WI_FI` to the customer onboarding taxonomy so the visible Wi-Fi choice is accepted and persisted by the backend.
- Made customer onboarding require nonblank first/last names and a five-digit ZIP before continuing.
- Removed the Chicago/60601 booking defaults. In-person bookings now start with the authenticated customer's profile ZIP and require street, city, two-letter state, and ZIP before continuing.
- Replaced deprecated directional customer icons with Compose auto-mirrored icons, preserving correct left-to-right and right-to-left navigation behavior without compiler deprecation warnings.
- Reverified the protected root `DigibuddyLogo.png`: 2,218,549 bytes, original timestamp unchanged, SHA-256 `4466746E888AF7FD0F97EF877FE1831CFB631AC3EBBDE52CC49B2538E175B6D9`. All six source resource copies remain byte-for-byte matches.

- Made the seeded Digibuddy welcome conversation explicitly read-only in the shared API contract, customer UI, coordinator, and backend authorization. The composer is hidden, direct send attempts are rejected server-side, and the message now points to the fictional development support number `+1 (312) 555-0100`.
- Fixed the customer chat send race in which the optimistic HTTP message and matching WebSocket delivery could coexist with the same Compose item key and crash the conversation. Incoming messages now replace matching client-message IDs deterministically.
- Fixed Helpers onboarding step advancement so a successful `Save and continue` response selects the backend-returned next step instead of retaining the old form.
- Fixed the Helpers legal-name onboarding save failure by declaring `application/json` on helper application-step requests, allowing the already-installed Ktor content-negotiation plugin to serialize `HelperApplicationStepRequest`. Added a MockEngine regression test that verifies the request reaches the transport with JSON content and the response decodes.
- Recorded ADR 0006 before completing H1. The customer profile, private helper application, and public helper catalog remain separate views of one authenticated identity.
- Added the nine helper states: `PROFILE_INCOMPLETE`, `IDENTITY_INFORMATION_REQUIRED`, `PAYMENT_ONBOARDING_REQUIRED`, `UNDER_REVIEW`, `CHANGES_REQUESTED`, `APPROVED`, `PAUSED_BY_HELPER`, `SUSPENDED`, and `REJECTED`.
- Retained H0 startup compatibility through the existing `onboardingStatus` field and added nullable `helperStatus`, progress, and paid-work eligibility fields.
- Added owner-authenticated application read, independent step save, submission, pause, and resume endpoints under `/api/v1/helper/application`.
- Added guided Compose screens for legal/public names, profile media placeholders, headline/biography, private home ZIP, service mode/area, skills, services, experience, languages, pricing, availability, optional certifications/portfolio, terms, and payout placeholder.
- Labeled onboarding data as Public or Private and Required or Optional. The server public snapshot allowlists only display/profile/service fields and excludes legal name, phone, home ZIP/address, identity documents, bank details, and tax details.
- Added Flyway V10 entities for helper applications, independently saved step payloads, requirements, profile status, approval events, required changes, and staff-review placeholders. Existing identity-linked helper profiles are compatibility-backfilled; fictional unlinked seeds are excluded.
- Added server review transitions and an approval transaction that records the decision and grants `HELPER` to the existing identity. The mobile app has no self-approval route.
- Added work-eligibility guards: both the shared helper role and `APPROVED` application status are required to activate services or receive paid requests. Paused and every unapproved state are denied.
- Added polished under-review, changes-requested, approved routing, paused, suspended, and rejected/support presentation.
- Added seven backend H1 tests for saved progress, work restrictions, role grant, private/public separation, field classification, requested-change resubmission, and invalid transitions. Helper routing now tests all nine states plus legacy response compatibility.

- Recorded ADR 0005 before scaffolding the provider client. No backend, database, identity, booking, chat, or payment model was duplicated.
- Added `:apps:helpers` with common Compose UI, Android/JVM/iOS targets, static `DigibuddyHelpers` framework, shared account authentication, and a Windows development preview.
- Added the AGP 9-compatible `:apps:helpers:androidApp` host using placeholder application ID `com.digibuddy.helpers`.
- Added `apps/helpers/iosApp/DigibuddyHelpers.xcodeproj` with SwiftUI lifecycle, placeholder bundle ID `com.digibuddy.helpers`, and Gradle framework embedding.
- Added `:shared:helper-onboarding` for server-driven lifecycle coordination and accessible onboarding/review/changes/suspension screens.
- Added `:shared:helper-dashboard` with exactly Requests, Jobs, Chats, and Profile tabs and polished, clearly labeled placeholder content.
- Added the additive authenticated `/api/v1/helper/startup` endpoint and shared client contract. It reads existing identity roles and V4 helper-profile account/approval data.
- Enforced startup precedence: suspension blocks entry; non-helper accounts route to onboarding; helper-role profiles route to under review, changes requested, approved app, or suspension.
- Added a desktop-only approved-workspace preview control that does not mutate roles, profile status, or any server state.
- Copied the protected source logo byte-for-byte to Helpers Compose, Android, and iOS resources with distinct derived filenames.
- Added backend lifecycle mapping tests, common startup routing tests, and an exact four-tab contract test.

- Replaced the raw vertically stacked development profile with a branded, accessible five-tab customer shell: Home, Find, Bookings, Chats, and Profile.
- Added reusable theme colors, cards, chips, avatars, empty states, responsive phone-width layouts, and aspect-fit source-logo rendering.
- Added authenticated helper search clients, list/map-style results, category/filter/sort controls, recent views, helper details, availability, portfolio examples, review summaries, and booking entry.
- Added a server-authoritative booking state machine covering request, quote, payment, confirmation, work, completion, cancellation, dispute, and refund states. Ownership, transition actor, overlap, idempotency, history, and allowed actions are enforced server-side.
- Added booking creation, grouped booking lists, detail/timeline screens, cancellation/reschedule commands, payment step, and receipt presentation.
- Added authenticated conversation/message APIs and WebSocket events with membership checks, idempotent client message IDs, ordered sequence values, read state, reconnect/offline queue behavior, retry, block, and report controls.
- Added a development payment provider, server-only Stripe configuration boundary, timestamped HMAC webhook verification, idempotent payment intents, immutable ledger entries, receipts, and booking confirmation only after authorization.
- Added push token/preference/delivery foundations, a safe local notification adapter, APNs configuration boundary, invalid-token cleanup, and validated deep-link routing.
- Added Flyway V6-V9 schemas for bookings, chat/realtime, payments/ledger/receipts, and notification devices/preferences/deliveries.
- Added focused tests for booking transitions/idempotency/ownership, chat idempotency and safety, payment authorization/receipt ownership, notification token lifecycle, and deep-link validation.
- Added deployment, operations, privacy, and App Store checklists. Production gaps are labeled explicitly rather than represented as completed integrations.

- Recorded ADR 0004 before implementation, selecting allowlisted helper summaries, authenticated reads, server-first eligibility, PostGIS geography matching, and deterministic UUID tie-breakers.
- Added V4 catalog entities for helper profiles, approval/verification/account status, skills, services/categories, service areas/ZIPs/radii, languages, availability, ratings/reviews/jobs, and response statistics.
- Added V5 fictional product fixtures: 13 requested categories, 10 skills, five languages, eight invented helpers, eight ZIP centroids, mixed prices/ratings/availability/service modes, and explicit pending/suspended exclusion records. No seed phone, email, street address, or real-person data was added.
- Added an indexed PostgreSQL/PostGIS repository using exact ZIP coverage plus `ST_DWithin` radius preselection, with a deterministic in-memory repository for tests/local fallback.
- Added authenticated endpoints for search, filter options, categories, helper summaries, and availability summaries.
- Added all requested filters, six sort choices, one-based pagination capped at 50, and immutable helper-ID tie-breaking.
- Added 11 tests covering exact ZIP, radius, remote matching, unapproved/suspended exclusion, combined filters, verification, all sorts, pagination, summary reads, authorization, and invalid/unknown ZIPs.

- Recorded ADR 0003 before implementation. The phone identity remains canonical; `customer_profile` shares its UUID and no helper/public DTO exposes the verified phone.
- Added `:shared:profile` with a simple, skippable Compose onboarding sequence and the authenticated profile/settings surface.
- Added customer profile/settings contracts and Ktor client calls for onboarding, name/ZIP/preferences, saved addresses, permissions/accessibility, security activity, photo uploads, export, and deletion.
- Added Flyway V3 tables for profiles, saved addresses, technology preferences, photo uploads, data-export requests, deletion requests, and account status.
- Added protected Ktor customer routes and service authorization. Required fields and US ZIP codes are validated server-side.
- Added local-development and hosted-presigned object-storage interfaces. JPEG, PNG, and WebP uploads are limited to 5 MiB and validated by declared metadata and file signature.
- Added trusted-device/recent-session profile views, export requests, fresh-authenticated soft deletion, all-session revocation, and an explicit active-booking deletion guard.
- Added eight backend tests for onboarding/skips, validation, updates/accessibility, upload rules and lifecycle, export/deletion/session handling, booking blocking/fresh authentication, and unauthorized access.
- Preserved authentication, booking, chat, and payment boundaries; bookings, chat, payments, and main tabs were not started.

- Recorded ADR 0002 before implementation, selecting phone-first identity, provider-isolated OTP, opaque rotating sessions, secure mobile token storage, and mandatory SMS second factor for optional email/password login.
- Added `:shared:authentication` with immutable customer auth state, accessible Compose screens, resend countdown, error/recovery states, email credential settings, and SMS OTP autofill semantics.
- Added backend E.164 normalization and validation with libphonenumber.
- Added random, per-attempt local OTP delivery and server-only Twilio Verify send/check integration. The development adapter is configuration-gated to local development and no production OTP is hard-coded.
- Added five-minute OTP expiry, 60-second resend delay, five-code attempt limit, 15-minute temporary lockout, per-phone and per-IP request limits, masked destinations, keyed redacted identifiers, and security audit events.
- Added phone signup/existing-account login plus optional email/password credentials using Argon2id (19 MiB, two iterations, parallelism one). Email/password never issues a session until an SMS second factor succeeds.
- Added opaque ten-minute access tokens and 30-day refresh sessions. Only token hashes are stored by the backend; refresh tokens rotate on use, prior hashes detect reuse, and current-device/all-device revocation is supported.
- Added trusted-device and session records with PostgreSQL/Hikari persistence plus an in-memory repository restricted to local development.
- Added Flyway migration V2 for user identities, roles, phone attempts, email credentials, trusted devices, refresh sessions/history, and audit events.
- Added iOS Keychain refresh-token storage and Android Keystore AES/GCM storage. Android preferences contain only ciphertext and its initialization vector; access tokens remain in memory.
- Added shared authentication HTTP contracts and Ktor client operations for normalize, start/resend/verify, refresh, email credential/login/second factor, current user, and logout endpoints.
- Added 15 authentication-service tests plus protected-route coverage. The required signup, repeat login, invalid/expired code, rate limit, token rotation/reuse, logout, all-device logout, two-step email login, and unauthorized-access cases are covered.
- Updated README, architecture, security, environment, and project-status documentation with the exact local OTP procedure and hosted configuration gates.

- Added the Gradle 9.5.0 Wrapper, official distribution checksum, Kotlin DSL settings/build files, central version catalog, and SHA-256 dependency verification metadata.
- Configured Kotlin Multiplatform 2.4.10 for Android, JVM, iOS device, and Apple-silicon iOS simulator targets.
- Configured Compose Multiplatform 1.11.1 with Material 3 1.9.0.
- Created the shared customer KMP/Compose module and thin Android host required by AGP 9.
- Created the shared contracts, core/domain, design-system, networking, and SQLDelight persistence modules.
- Created a SwiftUI Xcode host that embeds the `DigibuddyCustomer` framework, targets iOS 17, and isolates the placeholder `com.digibuddy.customer` bundle identifier in one xcconfig file.
- Added a minimal accessible customer screen with the logo, `Digibuddy` text, aspect-fit image scaling, and theme foundations.
- Preserved the root source logo and copied its exact bytes to Compose, Android, and iOS resource locations. No unsuitable square icon or branded splash derivative was generated.
- Added one Ktor backend for the future customer and helper apps, with a tested `GET /health` endpoint and shared serialized response contract.
- Added a platform-aware Ktor health API client. Android emulator development uses `10.0.2.2`; iOS/JVM use `127.0.0.1`.
- Added SQLDelight local-cache scaffolding and a JVM in-memory persistence test.
- Added Flyway migration infrastructure and the first backend migration, including PostGIS extension creation.
- Added local Docker Compose definitions for PostgreSQL 17 with PostGIS, Redis 8.2, and an opt-in Flyway migration service.
- Added `.env.example`; confirmed `.env` is ignored.
- Added explicitly non-production, no-op development placeholders for Twilio, Stripe, APNs, and S3-compatible object storage.
- Added ktlint, Detekt, Android lint, foundational tests, setup/build documentation, and ADR 0001 for the AGP 9 host split.

The prior note that tabs, bookings, chat, and payments were unimplemented is superseded by the C6-C12 development-preview work above.

## Architecture adjustment

AGP 9 does not allow the Android application plugin and Kotlin Multiplatform plugin in the same module. The requested customer application is therefore represented by:

- `:apps:customer` - shared KMP/Compose customer application and iOS framework
- `:apps:customer:androidApp` - thin Android application host

The reason and consequences are recorded in `docs/decisions/0001-agp9-platform-host-split.md`. `:shared:contracts` remains the previously documented addition that keeps backend/client DTOs independent from transport and persistence.

## Selected dependency baseline

| Component | Version |
| --- | ---: |
| JDK toolchain | 21 |
| Gradle Wrapper | 9.5.0 |
| Kotlin / Kotlin Multiplatform / Compose compiler | 2.4.10 |
| Android Gradle Plugin | 9.1.0 |
| Compose Multiplatform | 1.11.1 |
| Compose Material 3 | 1.9.0 |
| Ktor | 3.5.1 |
| kotlinx.serialization | 1.11.0 |
| kotlinx.coroutines | 1.11.0 |
| SQLDelight | 2.3.2 |
| Koin | 4.2.1 |
| Flyway | 11.8.2 |

All direct versions are centralized in `gradle/libs.versions.toml`. `gradle/verification-metadata.xml` records SHA-256 checksums for the resolved build graph.

## Logo integrity

| Property | Result |
| --- | --- |
| Source path | `DigibuddyLogo.png` |
| Format | PNG with alpha transparency |
| Dimensions | 1536 x 1024 pixels |
| File size | 2,218,549 bytes |
| Source SHA-256 | `4466746E888AF7FD0F97EF877FE1831CFB631AC3EBBDE52CC49B2538E175B6D9` |
| Resource-copy hashes | All three match the source SHA-256 exactly |

The root logo was not renamed, overwritten, recompressed, or edited.

## Commands run and results

| Command or check | Result |
| --- | --- |
| Read the attached H1-H10 roadmap plus `AGENTS.md`, README, architecture, project status, security model, existing helper/catalog/auth/booking/chat/payment code, contracts, networking, and client hosts | Completed before H1 implementation; H1 was executed first because later phases depend on its lifecycle and authorization model |
| First H1 combined compile | Failed before source checking because still-open H0 development processes/OneDrive held Kotlin incremental cache directories; Gradle daemons were stopped and shared contracts were rebuilt |
| Sandboxed `.\gradlew.bat --stop` attempt | Failed because the restricted wrapper cache attempted a denied network fetch and process inspection was denied; rerun with approved workspace tooling stopped one daemon |
| `.\gradlew.bat :shared:contracts:compileKotlinJvm --rerun-tasks --no-daemon` | Passed; repaired the interrupted shared-contract incremental output |
| `.\gradlew.bat :shared:networking:compileKotlinJvm :backend:compileKotlin --rerun-tasks --no-daemon` | Passed |
| `.\gradlew.bat :shared:helper-onboarding:compileKotlinJvm :apps:helpers:compileKotlinJvm :apps:helpers:androidApp:compileDebugKotlin --rerun-tasks --no-daemon` | Passed |
| First H1 focused test run | Failed: one invalid-transition test expected a status error before creating its application and correctly received `HELPER_APPLICATION_NOT_FOUND`; fixture corrected without weakening production checks |
| `.\gradlew.bat :backend:test :shared:helper-onboarding:jvmTest :shared:networking:jvmTest :apps:helpers:jvmTest --no-daemon` | Passed after the fixture correction |
| First two H1 `ktlintFormat` runs | Failed on non-auto-correctable 120-character lines; lines were wrapped and targeted formatting passed |
| First H1 `ktlintCheck detekt` | Formatting passed; Detekt reported validation complexity and transactional JDBC nesting/generic rollback catches. Established file-level suppressions were added for those intentional bounded cases and two SQL lines were wrapped |
| `.\gradlew.bat :backend:ktlintFormat ktlintCheck detekt --no-daemon` | Passed repository-wide |
| `.\gradlew.bat build :apps:customer:androidApp:assembleDebug :apps:helpers:androidApp:assembleDebug :apps:customer:compileKotlinIosSimulatorArm64 :apps:helpers:compileKotlinIosSimulatorArm64 --no-daemon` | Passed in 95 seconds; complete customer/helper/shared/backend graph, Android lint/packaging, JVM tests, and both iOS source targets completed |
| Parsed current Gradle JUnit XML reports after the full H1 build | 62 tests, 0 failures, 0 errors, 0 skipped: backend 54; customer 1; contracts/core/database/networking 1 each; helper onboarding 2; helper dashboard 1 |
| Root logo SHA-256 and byte-size recheck | Passed; protected logo remains 2,218,549 bytes with SHA-256 `4466746E888AF7FD0F97EF877FE1831CFB631AC3EBBDE52CC49B2538E175B6D9` |
| Docker detection for V10 migration execution | Docker remains unavailable, so PostgreSQL migration/transaction integration is unverified on this host |
| Final secret-pattern scan, `.env` ignore check, and four-way Helpers logo hash/size comparison | Passed; no production credential patterns found, `.env` is ignored, and all logo copies remain exact 2,218,549-byte matches |
| Final full regression after adding the V10 compatibility backfill | Passed in 21 seconds; 972 tasks, including updated backend resources, 62 tests, both Android apps/lint, and both iOS simulator Kotlin targets |
| Read `AGENTS.md`, README, architecture, project status, security model, backend auth/booking/chat/payment/catalog models and routes, shared contracts/networking/auth/design modules, and existing customer hosts before H0 changes | Completed before implementation |
| `.\gradlew.bat projects :backend:compileKotlin :apps:helpers:compileKotlinJvm :apps:helpers:androidApp:compileDebugKotlin --no-daemon` | Passed; new modules were recognized and backend, Helpers JVM, and Helpers Android Kotlin compiled |
| `.\gradlew.bat :backend:test :shared:helper-onboarding:jvmTest :shared:helper-dashboard:jvmTest :apps:helpers:jvmTest :apps:helpers:compileKotlinIosSimulatorArm64 --no-daemon` | Passed; targeted backend/helper tests and Helpers iOS simulator source compilation completed |
| First `.\gradlew.bat ktlintFormat ktlintCheck detekt --no-daemon` | Failed because independent format/check tasks ran concurrently and the checker saw the preformatted dashboard; no compile or test failure |
| `.\gradlew.bat ktlintFormat --no-daemon` | Passed and formatted the helper sources |
| `.\gradlew.bat ktlintCheck detekt --no-daemon` | Passed when run after formatting |
| `.\gradlew.bat build :apps:customer:androidApp:assembleDebug :apps:helpers:androidApp:assembleDebug :apps:customer:compileKotlinIosSimulatorArm64 :apps:helpers:compileKotlinIosSimulatorArm64 --no-daemon` | Passed in 84 seconds; complete customer/helper/shared/backend graph, Android lint/packaging, JVM tests, and both iOS source targets completed |
| Parsed current Gradle JUnit XML reports | 54 tests, 0 failures, 0 errors, 0 skipped: backend 47; customer 1; contracts/core/database/networking 1 each; helper onboarding 1; helper dashboard 1 |
| SHA-256 and byte-size check for root logo and three Helpers resource copies | Passed; all four are 2,218,549 bytes and hash to `4466746E888AF7FD0F97EF877FE1831CFB631AC3EBBDE52CC49B2538E175B6D9` |
| Replaced the stale July 16 backend preview process, then `.\gradlew.bat :backend:run --no-daemon` | Current backend started successfully from the H0 source graph |
| Live `GET /health` and unauthenticated `GET /api/v1/helper/startup` | Health returned `ok`; helper startup correctly returned HTTP 401 without an access token |
| Live local OTP signup/verification followed by authenticated `GET /api/v1/helper/startup` | Passed; a normal shared account returned `hasHelperRole=false`, `onboardingStatus=ONBOARDING`, and no helper state was created or mutated |
| `.\gradlew.bat :apps:helpers:run --no-daemon` | Launched the phone-sized Digibuddy Helpers Windows preview for visual testing |
| Read C6-C12 attached phase specification plus `AGENTS.md`, architecture, security, development plan, and prior status | Completed before the marketplace implementation |
| `rg` secret-pattern review across handwritten repository files | No committed production credentials found; only disposable local Compose values, configuration lookups, form-field names, and provider-prefix validation matched |
| `.\gradlew.bat :backend:compileKotlin :apps:customer:compileKotlinJvm --no-daemon` | Passed after resolving WebSocket configuration and payment UI state compilation issues |
| `.\gradlew.bat :backend:test :apps:customer:jvmTest --no-daemon` | Passed; booking, chat, payment, notification, deep-link, and prior test suites completed |
| `.\gradlew.bat ktlintFormat ktlintCheck detekt --no-daemon` | Passed after formatting handwritten sources, excluding generated SQLDelight max-line output, and simplifying flagged service composition/control flow |
| First combined full build command | Timed out at the command runner's 124-second limit without a reported compile/test failure; rerun with a longer limit |
| `.\gradlew.bat build :apps:customer:androidApp:assembleDebug :apps:customer:compileKotlinIosSimulatorArm64 --no-daemon` | Passed in 47 seconds; 637 tasks, Android debug APK assembled, lint passed, JVM tests passed, and iOS simulator Kotlin source compiled |
| `docker --version` and `docker compose version` | Could not run: Docker is not installed/detectable on this Windows host |
| Xcode/iOS framework link and simulator execution | Not applicable on Windows; must run on macOS with Xcode |
| `.\gradlew.bat :backend:run --no-daemon` plus `Invoke-RestMethod http://127.0.0.1:8080/health` | Backend started; health response was `{"status":"ok","service":"digibuddy-backend"}` |
| `.\gradlew.bat :apps:customer:run --no-daemon` | Polished Windows customer preview launched and left running for user testing |
| `.env` ignore check and source/resource logo SHA-256 comparison | `.env` is ignored; all resource copies still exactly match the protected root logo hash and 2,218,549-byte size |
| Read `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT_STATUS.md`, `docs/DEVELOPMENT_PLAN.md`, and `docs/SECURITY_MODEL.md` | Completed before implementation |
| Recursive repository inventory and Git status checks | Completed; preserved existing files and confirmed Git was already initialized |
| Java, Git, Android SDK/Studio, Docker, and Xcode detection checks | Completed; environment recorded below |
| Gradle 9.4.1 `wrapper --gradle-version 9.5.0 --distribution-type bin` | Wrapper generated; distribution and wrapper JAR checksums verified |
| `./gradlew` first sandboxed wrapper access | Failed because restricted networking denied the distribution check; rerun with approved network access succeeded |
| `.\gradlew.bat projects --no-daemon --stacktrace` | First run failed because the root Detekt version-catalog lookup used child-project scope; fixed |
| `.\gradlew.bat projects --no-daemon` | Passed; all requested modules were recognized |
| `.\gradlew.bat build --no-daemon` during implementation | Reproducible failures found and fixed: Detekt config section, Ktor imports, ktlint formatting/generated sources, networking transport leakage, and direct Material 3 version |
| `.\gradlew.bat ktlintFormat --no-daemon` | First run exposed generated SQLDelight files in lint input; generated sources were excluded without disabling handwritten-code lint |
| `.\gradlew.bat :shared:database:ktlintCheck --rerun-tasks --no-daemon` | Passed after generated-source filtering was corrected |
| `.\gradlew.bat :shared:networking:ktlintFormat :apps:customer:ktlintFormat --no-daemon` | Passed |
| `.\gradlew.bat :shared:core:jvmTest :shared:contracts:jvmTest :shared:database:jvmTest :shared:networking:jvmTest :backend:test --rerun-tasks --no-daemon --parallel` | Passed; 6 tests, 0 failures, 0 errors, 0 skipped |
| `.\gradlew.bat :apps:customer:androidApp:compileDebugKotlin --rerun-tasks --no-daemon` | First direct-dependency run found an invalid Material 3 `1.11.1` assumption; pinned independently versioned Material 3 `1.9.0`, reran, passed |
| `.\gradlew.bat :apps:customer:compileKotlinIosSimulatorArm64 :apps:customer:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks --no-daemon` | Kotlin/Native simulator source compilation passed; framework link task was skipped on Windows as unsupported |
| `.\gradlew.bat ktlintCheck detekt --rerun-tasks --no-daemon --parallel` | Passed |
| `.\gradlew.bat build --write-verification-metadata sha256 --no-daemon` | Passed; generated resolved-artifact SHA-256 verification metadata |
| `.\gradlew.bat build --no-daemon` after verification metadata generation | Passed; 483-task graph evaluated, dependency verification active, Android debug/release artifacts and all applicable checks completed |
| `.\gradlew.bat --version --no-daemon` | First sandboxed run could not access the network; approved rerun passed and reported Gradle 9.5.0 on JDK 21.0.8 |
| `Get-FileHash` on root and three resource logos | Passed; all four hashes identical |
| `git check-ignore -v .env` | Passed; `.env` matched repository ignore rule |
| Docker and `xcodebuild` command detection | Both unavailable |
| Python YAML fallback check | Could not run because PyYAML is not installed; no package was installed |
| Read `AGENTS.md`, architecture, status, development plan, and security model for Phase 2 | Completed before authentication changes |
| `.\gradlew.bat :backend:compileKotlin --write-verification-metadata sha256 --no-daemon` | First run found Ktor 3 imports and invalid `@Synchronized` use on suspending functions; fixed. Rerun compilation passed and dependency hashes were updated |
| `.\gradlew.bat :shared:authentication:compileKotlinIosSimulatorArm64 --no-daemon` | First runs found Core Foundation interop imports/value access; fixed. Final Kotlin/Native source compilation passed |
| `.\gradlew.bat :apps:customer:androidApp:compileDebugKotlin --no-daemon` | First run found the auth module was not exposed to the thin host; changed the KMP dependency to API. Rerun passed |
| `.\gradlew.bat :backend:test --no-daemon` | First run after adding auth tests exposed missing test config defaults; fixed with safe local defaults. Final run passed: 18 backend tests |
| `.\gradlew.bat ktlintFormat --no-daemon` | Failed on a generated SQLDelight long line; no generated file was edited. Targeted formatter tasks for changed modules passed |
| `.\gradlew.bat ktlintCheck detekt --no-daemon --parallel` | First run found intentional repository/service complexity; added narrow source-level suppressions and fixed formatting. Checks later passed in the full build |
| `.\gradlew.bat build --no-daemon` | First run found two backend function-signature formatting issues; fixed. Final run passed: 558-task graph, tests, Detekt, ktlint, Android debug/release packaging, and lint |
| `.\gradlew.bat :shared:core:jvmTest :shared:contracts:jvmTest :shared:database:jvmTest :shared:networking:jvmTest :shared:authentication:jvmTest :apps:customer:androidApp:compileDebugKotlin :apps:customer:compileKotlinIosSimulatorArm64 --no-daemon --parallel` | Passed; authentication JVM test task currently has no source tests, while the backend owns the authentication rules |
| `docker compose --env-file .env.example -f infrastructure/docker-compose.yml config` | Could not run: Docker CLI is not installed |
| `Get-FileHash` on root and resource logo copies after Phase 2 | Passed; all hashes remain `4466746E888AF7FD0F97EF877FE1831CFB631AC3EBBDE52CC49B2538E175B6D9` |
| Read `AGENTS.md`, architecture, status, development plan, and security model for Phase 3 | Completed before profile changes |
| `.\gradlew.bat :backend:test --no-daemon --no-build-cache` | Initial test compile found three fixture type mismatches; fixed. Final run passed: 26 backend tests |
| `.\gradlew.bat :apps:customer:compileKotlinJvm :apps:customer:androidApp:assembleDebug :backend:test --no-daemon --no-build-cache` | Passed |
| `.\gradlew.bat ktlintCheck detekt --no-daemon --no-build-cache` | Initial run found formatting and one name-validation complexity finding; targeted formatting and refactor completed |
| `.\gradlew.bat :backend:detekt :backend:ktlintCheck :shared:profile:ktlintCheck :apps:customer:ktlintCheck :shared:networking:ktlintCheck :shared:contracts:ktlintCheck --no-daemon --no-build-cache` | Passed after refactor; the root formatter still reports a pre-existing generated SQLDelight long line and no generated file was edited |
| `.\gradlew.bat build --no-daemon` | Passed: 628-task graph; backend/shared tests, static analysis, Android debug/release packaging, and Android lint completed |
| `.\gradlew.bat :shared:profile:compileKotlinIosSimulatorArm64 :apps:customer:compileKotlinIosSimulatorArm64 --no-daemon` | Passed; native framework linking remains skipped on Windows |
| Initial full-build verification with `--no-build-cache` | The command runner timed out after 62 seconds while Kotlin/Native was still compiling; the ordinary full-build rerun completed successfully |
| Initial sandboxed iOS source-compilation rerun | Wrapper distribution access was denied by the network sandbox; the approved rerun passed |
| `.\gradlew.bat :shared:authentication:ktlintCheck :shared:profile:ktlintCheck :apps:customer:ktlintCheck :shared:profile:compileKotlinIosSimulatorArm64 :apps:customer:compileKotlinIosSimulatorArm64 --no-daemon` | Passed after the final account-settings UI connection |
| `Get-FileHash` on root and resource logo copies after Phase 3 | Passed; all hashes remain `4466746E888AF7FD0F97EF877FE1831CFB631AC3EBBDE52CC49B2538E175B6D9` |
| `docker --version` | Could not run: Docker CLI is not installed |
| Read `AGENTS.md`, architecture, status, development plan, and security model for Phase 4 | Completed before catalog changes |
| `.\gradlew.bat :shared:contracts:ktlintFormat :backend:ktlintFormat :backend:compileKotlin :backend:compileTestKotlin --no-daemon --no-build-cache` | Passed |
| `.\gradlew.bat :backend:test --no-daemon --no-build-cache` | Passed: 37 backend tests; 0 failures, 0 errors |
| `.\gradlew.bat :backend:detekt :backend:ktlintCheck :shared:contracts:ktlintCheck --no-daemon --no-build-cache` | First run reported catalog filter/parser complexity and seed-fixture parameter count; narrow intentional suppressions were added |
| `.\gradlew.bat :backend:ktlintFormat :backend:detekt :backend:ktlintCheck :shared:contracts:ktlintCheck :backend:test --no-daemon --no-build-cache` | Passed after fixes |
| Docker, `psql`, and PostgreSQL service detection plus Docker Compose validation | Failed/unavailable: none of Docker, `psql`, or a PostgreSQL Windows service exists on this host; V4/V5 were not executed |
| First `.\gradlew.bat build --no-daemon` after catalog work | Command runner timed out after 64 seconds while Kotlin/Native tasks were active; no compilation failure was reported |
| Final `.\gradlew.bat build --no-daemon` | Passed: 628-task graph; all applicable tests, static analysis, Android packaging/lint, and Kotlin/Native compilation completed |
| `Get-FileHash DigibuddyLogo.png -Algorithm SHA256` after Phase 4 | Passed; hash remains `4466746E888AF7FD0F97EF877FE1831CFB631AC3EBBDE52CC49B2538E175B6D9` |
| `\.\gradlew.bat :backend:run --no-daemon` preview smoke check (2026-07-16) | Initial run failed because Ktor reflected the overloaded `module` function and attempted parameter injection; startup configuration now targets the unique `configuredModule` entry point |
| `\.\gradlew.bat :backend:test --no-daemon` after startup correction | Passed |
| `Invoke-RestMethod http://127.0.0.1:8080/health` | Passed while the local backend was running: `{"status":"ok","service":"digibuddy-backend"}` |
| Android Studio sync diagnosis (2026-07-16) | Failed because strict dependency verification did not yet contain checksums for IDE-requested dependency source JARs; checksums were generated with Gradle's verification metadata writer |
| `.\gradlew.bat prepareKotlinIdeaImport --write-verification-metadata sha256 --no-daemon` | Passed; recorded checksums for the exact IDE-import artifacts |
| `.\gradlew.bat prepareKotlinIdeaImport --no-daemon` | Passed with normal strict dependency verification: 26 actionable tasks |
| `.\gradlew.bat :apps:customer:androidApp:assembleDebug --no-daemon` after IDE-sync fix | Passed: Android debug APK assembled successfully |
| Android Studio tooling-model source resolution from the reported `jvmDevCompileClasspath` failure | Resolved all 42 reported source JARs and recorded their exact SHA-256 checksums; strict verification remained enabled |
| Strict re-resolution of the 42 reported IDE source artifacts without metadata-writing mode | Passed: all 42 artifacts verified successfully |
| Final `.\gradlew.bat :apps:customer:androidApp:assembleDebug --no-daemon` after tooling-model checksum update | Passed: 145-task Android debug build graph |
| Added development-only Compose Desktop launcher for the existing customer JVM target | Completed; provides a phone-sized Windows preview using the shared UI and local backend, with an in-memory refresh-token store |
| `.\gradlew.bat :apps:customer:compileKotlinJvm --no-daemon` | Passed after adding the Windows preview launcher |
| `.\gradlew.bat :apps:customer:run --no-daemon` | Launched; the preview process remained active and the backend `/health` endpoint returned `ok` |
| Read `docs/ARCHITECTURE.md`, `docs/SECURITY_MODEL.md`, and `docs/PROJECT_STATUS.md` for local Android build diagnosis | Completed before build and launch checks |
| `.\gradlew.bat :apps:customer:androidApp:assembleDebug --no-daemon --stacktrace` | Passed: Android debug APK assembled successfully |
| `.\gradlew.bat build --no-daemon --stacktrace` | Passed: 628-task graph; Android packaging/lint, backend tests, static analysis, and Kotlin/Native compilation completed |
| `adb devices` | Failed in the shell because `adb` is not on `PATH`; the SDK-local `adb.exe` was used instead |
| Android SDK/emulator/AVD detection commands | Found `platform-tools\adb.exe`, `emulator.exe`, and an attached `emulator-5554` device; `.android\avd` folders exist, but `emulator.exe -list-avds` returned no names in this shell |
| `.\gradlew.bat :apps:customer:androidApp:installDebug --no-daemon --stacktrace` | Passed: installed `androidApp-debug.apk` on the running `Pixel_6` AVD |
| SDK-local `adb shell am start -n com.digibuddy.customer/com.digibuddy.customer.android.MainActivity` | Passed: app launch command succeeded and the activity became resumed |
| First `:shared:networking:jvmTest :shared:networking:ktlintCheck :apps:helpers:compileKotlinJvm` hotfix run | Implementation compiled; check failed on formatting in the new regression test. No passing test result was claimed |
| Formatter/check reruns for the helper request hotfix | The combined formatter/check invocation raced independent Gradle tasks and reported the pre-format result; the next check caught a missing required `progressPercent` test-fixture field, which was added |
| `.\gradlew.bat :shared:networking:ktlintCheck :shared:networking:jvmTest :apps:helpers:compileKotlinJvm --no-daemon` | Passed after the helper request fix: formatting clean, both networking JVM tests passed, and Helpers desktop Kotlin compilation passed |
| `.\gradlew.bat :shared:contracts:ktlintFormat :backend:ktlintFormat :apps:customer:ktlintFormat :shared:helper-onboarding:ktlintFormat --no-daemon` | Passed; formatted the read-only welcome, duplicate-safe chat, and onboarding advancement changes |
| Focused backend/customer/helper tests, both desktop compilations, both iOS simulator Kotlin compilations, and targeted ktlint checks | Passed in 48 seconds: backend 55 tests, customer 2 tests, helper onboarding 2 tests; both iOS source targets compiled |
| `.\gradlew.bat :apps:customer:androidApp:compileDebugKotlin :apps:helpers:androidApp:compileDebugKotlin --no-daemon` | Passed for both retained Android hosts |
| `.\gradlew.bat build --no-daemon` after chat/onboarding corrections | Passed in 76 seconds; complete repository graph, 65 discovered JVM tests, static analysis, Android packaging/lint, and applicable Kotlin/Native compilation completed |

## Test results

| Area | Result |
| --- | --- |
| Shared core JVM test | Passed: 1 test |
| Shared contract serialization JVM test | Passed: 1 test |
| SQLDelight persistence JVM test | Passed: 1 test |
| Shared networking JVM tests | Passed: 2 tests, covering health response decoding and helper application-step JSON serialization |
| Backend authentication, health, profile, catalog, marketplace, chat, helper-startup, and helper-application tests | Passed: 55 tests; 0 failures, 0 errors, 0 skipped |
| Customer JVM tests | Passed: 2 tests covering deep links and duplicate-safe chat delivery merging |
| Development-adapter safety test | Passed: 1 test |
| Android debug Kotlin compilation | Passed from rerun inputs |
| Android debug/release packaging and Android lint | Passed in full Gradle build |
| Android emulator install and launch | Passed: debug APK installed on the `Pixel_6` AVD and `com.digibuddy.customer/.android.MainActivity` became the resumed activity |
| ktlint and Detekt | Passed from rerun inputs |
| iOS simulator Kotlin/Native compilation | Passed from rerun inputs |
| iOS framework linking and Xcode app build | Skipped/unverified: requires macOS and Xcode |
| Docker Compose `config` validation | Skipped/unverified: Docker CLI is not installed |
| Helper onboarding routing JVM tests | Passed: 2 tests |
| Helper four-tab contract JVM test | Passed: 1 test |
| Complete discovered JVM test result set | Passed: 65 tests; 0 failures, 0 errors, 0 skipped |

## Available and missing local tools

| Tool | Result |
| --- | --- |
| Operating system | Windows 11 x64, kernel/version line 10.0.26200.8524 |
| Java | Microsoft OpenJDK 21.0.8 LTS |
| Git | 2.46.0.windows.1 |
| Android Studio | 2026.1.1 |
| Android SDK | Installed; API 35 and compatible build tools available |
| Android platform tools | Installed under the Android SDK; `adb` is not on the shell `PATH` |
| Android emulator | Emulator and API 33/API 36 system images are installed; `Pixel_6` was attached as `emulator-5554` during the local launch check |
| Gradle | Repository Wrapper 9.5.0 available; no standalone install required |
| Xcode | Missing/unavailable on Windows |
| Docker | Missing |
| IntelliJ IDEA | Not separately detected; Android Studio is available |

No system-wide software was installed.

## Known limitations and risks

- H1 implements helper application saving, submission, status, review events, requested changes, shared-role approval, and eligibility guards. It does not yet implement H2 media uploads/public-profile editing or H3-H9 helper work, chat, payout, notification, and cross-app workflows.
- New local phone accounts begin with only `CUSTOMER`; starting helper onboarding creates a separate application attached to that identity. Only server review can grant `HELPER`. The desktop-only approved-workspace preview still does not change server state and is not enabled in Android/iOS compositions.
- The PostgreSQL V10 repository and approval/role transaction compile and have in-memory contract coverage, but Docker/PostgreSQL are unavailable on this host, so Flyway execution, compatibility backfill, JSONB step persistence, and transaction rollback behavior require integration testing.
- H1 profile picture and banner fields are explicitly development placeholders. Crop, compression, upload progress/cancel/retry, server file validation, presigned upload, moderation, and customer preview belong to H2.
- Future helper booking/chat/payment APIs must add relationship, ownership, eligibility, and transition authorization to the single backend; a role or approved UI route alone is never authorization.
- `+1 (312) 555-0100` is a reserved fictional development support number. Product ownership must supply and verify a real support number before TestFlight external testing or release.
- The Helpers iOS Kotlin source and Xcode project structure are present, but framework linking, Keychain runtime behavior, signing, and launch require macOS with Xcode.

- The iOS source graph cross-compiles on Windows, but only Xcode on macOS can link, sign, launch, and archive the native host. The hand-authored project must be opened and built there before Phase 1 can close.
- The iOS Keychain implementation compiles for the simulator and device source sets but cannot be runtime-tested on Windows. Verify save/read/delete behavior and SMS autofill on a real simulator/device before release.
- Docker Compose schema interpolation and container startup are not validated on this host because Docker is missing. The YAML and migration wiring require a Docker-capable verification pass.
- PostgreSQL V2 migrations and the JDBC repository have not been exercised against a running PostGIS container on this host.
- PostgreSQL V3 has not been executed on this host, and customer-profile runtime storage is currently the local in-memory repository. A PostgreSQL customer-profile repository must be bound before hosted use.
- PostgreSQL/PostGIS V4 and fictional seed V5 have not been executed on this host. The JDBC query compiles and unit behavior is covered through the repository contract, but `ST_DWithin`, indexes, Flyway ordering, and seed SQL require a real PostGIS integration run.
- Choose Photo, Take Photo, crop/compress, operating-system permission prompts, and biometric unlock are represented by shared capability boundaries and UI actions, but their Android/iOS runtime adapters are not yet bound. The upload retry/progress pipeline and server validation are implemented.
- Several settings rows remain presentation placeholders pending platform adapters or dedicated account flows: change phone, verified-email confirmation, biometric gating, notification/location permission prompts, and legal/help content. Name, ZIP, saved addresses, accessibility preferences, notification enablement, account sign-in settings, security activity, export, and deletion are connected.
- Twilio Verify send/check behavior has not been exercised with sandbox credentials. Local tests use only the random development adapter.
- SMS OTP is vulnerable to phishing, SIM-swap, and carrier risks. Customer phone login meets this phase's scope, but helper/support/admin accounts and recovery need a phishing-resistant factor decision.
- Database-backed rate checks are implemented and tested through the repository contract, but a multi-instance deployment should move counters/locks to atomic Redis operations or equivalent before internet-scale rollout.
- Kotlin/Native metadata compilation reports duplicate KLIB unique-name warnings across transitive Compose/AndroidX/SQLDelight metadata. Compilation succeeds, but the warnings should be rechecked during the macOS framework link.
- Gradle reports deprecations from the current plugin ecosystem that will matter for Gradle 10; the chosen supported Gradle 9.5 line builds successfully.
- CI workflows are not yet created, so cross-host verification is manual.
- OneDrive-hosted Gradle projects can encounter synchronization, locking, and path-length issues; keep caches outside the repository and monitor repeat builds.
- The Git user-level ignore file remains unreadable in this managed environment, but repository ignore checks work.

## 2026-07-20 cross-app catalog, pricing, and icon correction

Completed:

- Removed helper-entered customer pricing. The compatible onboarding step now records acknowledgment that Digibuddy sets rates.
- Added a shared price schedule for $29 quick remote help, $49 Standard Help (30–60 minutes), and a $79 in-home visit.
- Added informational membership cards for $9.99/10 issues, $19.99/30 issues, and $99.99/unlimited help per month. Enrollment and recurring billing remain disabled and clearly labeled as coming later.
- Made booking labor totals backend-derived from the platform schedule and removed the previous added 12% fee from the displayed fixed total.
- Added a development-only post-submission approval action. It grants the existing shared helper role through the normal review transition and projects allowlisted helper data into the in-memory customer catalog.
- Kept the development approval route absent unless `digibuddy.environment=local-development`; Android and iOS UI do not expose the control.
- Replaced mojibake character glyphs in Helpers navigation/status presentation with Material Compose icons and corrected remaining corrupted helper preview punctuation.
- Recorded the decision in ADR 0007.

Commands and results:

| Command | Result |
| --- | --- |
| `./gradlew :backend:compileKotlin :apps:customer:compileKotlinJvm :apps:helpers:compileKotlinJvm :shared:helper-onboarding:jvmTest :shared:helper-dashboard:jvmTest --stacktrace` | Passed |
| `./gradlew :shared:core:allTests :backend:test :apps:customer:jvmTest :apps:helpers:jvmTest --stacktrace` | Failed once: the health test intentionally had no `digibuddy.environment` property; configuration was made optional. No passing result was claimed for this run. |
| `./gradlew :backend:test :shared:core:jvmTest :apps:customer:jvmTest :apps:helpers:jvmTest --stacktrace` | Passed; backend, shared pricing, and customer tests completed; Helpers currently has no app-level JVM test sources. |
| `./gradlew build --no-daemon --stacktrace` | Passed after the pricing/catalog/icon correction; full repository build, checks, Android packaging, and applicable Kotlin/Native compilation completed. |
| `./gradlew :backend:ktlintFormat :backend:test :shared:core:jvmTest :apps:customer:androidApp:compileDebugKotlin :apps:helpers:androidApp:compileDebugKotlin :apps:customer:compileKotlinIosSimulatorArm64 :apps:helpers:compileKotlinIosSimulatorArm64 --no-daemon` | Passed; 58 backend tests with 0 failures/errors, both Android debug Kotlin targets, and both iOS simulator Kotlin source targets. |
| Targeted Detekt and ktlint checks for backend, shared pricing/networking/helper modules, and both apps | Passed. |
| `./gradlew :shared:helper-onboarding:jvmTest :apps:helpers:compileKotlinJvm --no-daemon` after local-approval refresh fix | Passed. The stale Helpers preview was replaced and the backend health check remained `ok`. |

Known limitations:

- Local helper projection is intentionally in-memory and is lost when the backend restarts. Production PostgreSQL projection and staff review tooling remain future work.
- A helper must complete and submit the application before the desktop-only local approval action appears. Pending and incomplete helpers remain excluded from customer search.
- Memberships are presentation-only. No subscription, entitlement counter, recurring charge, renewal, cancellation, or refund behavior exists yet.
- Standard Help is shown in the product price catalog; a dedicated booking-tier selector still needs product-definition work before checkout can sell that tier.
- iOS framework source compilation can run on Windows, but native linking, signing, and device testing still require macOS/Xcode.

Next phase: verify the local end-to-end flow by completing and submitting a helper application, selecting **Approve for local testing**, then refreshing customer search for the same ZIP. Separately define Standard Help booking eligibility and membership entitlement/billing rules before implementing checkout.

## 2026-07-20 complete local journey verification for ZIP 32539

Completed and verified:

- Exercised a fictional customer, Maria Rivera, through phone signup, minimal onboarding, `32539` profile/address settings, accessibility settings, helper search, remote and in-home booking requests, welcome-chat safety, data export, optional email/password with SMS second factor, and fresh-authenticated deletion.
- Exercised a fictional helper, Charles Han, through phone signup, every required onboarding step, submission, under-review exclusion, labeled local approval, customer discovery, pause, resume, and logout.
- Verified the customer does not see Charles before approval or while paused, and does see him after approval/resume in `32539`.
- Verified the backend ignores a tampered `expectedLaborCents = 1` request and returns the server price of `$29` for quick remote help and `$79` for an in-home visit.
- Verified booking idempotency, booking ownership privacy, anonymous rejection, refresh rotation/reuse detection, deletion session revocation, and both current-device and all-device logout.
- Verified the welcome conversation is non-replyable, contains `Welcome to Digibuddy`, displays the fictional support number `+1 (312) 555-0100`, and rejects direct reply attempts without crashing.
- Generated both Android debug APKs. Android lint reports contain zero issues.
- Cross-compiled both customer and helper iOS simulator Kotlin source targets. The framework link tasks were requested and correctly reported `SKIPPED` because this host is Windows.

Commands and results:

| Command | Result |
| --- | --- |
| `.\gradlew.bat :shared:helper-onboarding:ktlintFormat :shared:networking:ktlintFormat :backend:ktlintFormat :shared:helper-onboarding:jvmTest :shared:networking:jvmTest :backend:test --no-daemon` | Passed after adding helper validation, friendly network errors, ZIP `32539`, and catalog regression coverage. |
| `.\gradlew.bat :backend:ktlintFormat :shared:profile:ktlintFormat :backend:test --no-daemon` | Passed after pause/resume synchronization and Wi-Fi onboarding taxonomy fixes. |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-local-journeys.ps1 -ZipCode 32539` | Final connected run passed 92/92 checks. Earlier harness runs stopped on three assertions; two were JSON/default or text-matching false positives, and one exposed invalid ordering between deliberate refresh reuse and deletion. The harness was corrected and no pass was claimed for those partial runs. |
| `.\gradlew.bat clean build ktlintCheck detekt --no-daemon` | Failed once at `:shared:profile:ktlintCommonMainSourceSetCheck` due function-signature whitespace added during this verification. No passing result was claimed. |
| `.\gradlew.bat :shared:profile:ktlintFormat :apps:customer:ktlintFormat --no-daemon` | Passed and corrected the formatting failure. |
| `.\gradlew.bat build ktlintCheck detekt --no-daemon` | Passed in 80 seconds after the clean build; 75 JVM tests, 0 failures, 0 errors, 0 skipped. Both Android debug APKs were packaged and applicable Kotlin/Native compilations completed. |
| `.\gradlew.bat :apps:customer:androidApp:lintDebug :apps:helpers:androidApp:lintDebug --no-daemon` | Passed; both lint XML reports contain zero issues. |
| `.\gradlew.bat :apps:customer:linkDebugFrameworkIosSimulatorArm64 :apps:helpers:linkDebugFrameworkIosSimulatorArm64 --no-daemon` | Gradle build passed; both iOS simulator Kotlin compilations passed/up-to-date and both native framework link tasks were `SKIPPED` on Windows. |
| `.\gradlew.bat :apps:customer:ktlintFormat :apps:customer:jvmTest :apps:customer:compileAndroidMain :apps:customer:compileKotlinIosSimulatorArm64 --no-daemon` | Passed after adding the customer-profile ZIP booking-draft regression test. Current discovered JVM total: 76 tests, 0 failures/errors/skips. |
| `.\gradlew.bat :apps:customer:ktlintCheck :apps:customer:jvmTest :apps:customer:compileAndroidMain :apps:customer:compileKotlinIosSimulatorArm64 --no-daemon` | Passed after replacing deprecated customer Sort/Back icons with auto-mirrored variants; the previous customer icon compiler warnings are gone. |
| `.\gradlew.bat build ktlintCheck detekt --no-daemon` (final source state) | Passed in 55 seconds; 76 current JVM tests have 0 failures/errors/skips, Android packages and lint completed, and applicable iOS compilation completed. |
| `docker compose -f infrastructure\docker-compose.yml config --quiet` | Not run successfully: `docker` is not installed on this host. No Compose validation pass is claimed. |
| `.\gradlew.bat help --warning-mode all --no-daemon` | Passed and isolated the Gradle 10 deprecation warning to Detekt 1.x applying the deprecated `ReportingExtension.file(String)` API at root `build.gradle.kts:28`. It is a warning, not the cause of a failed build. |

Known limitations found by the journey audit (superseded where noted by the next phase update):

- The earlier Requests/Jobs/Chats placeholder limitation is resolved by the real helper workspace described below.
- A helper can now accept or decline a request. A complete provider quote, customer payment authorization, work execution, and completion journey remains future work.
- The Standard Help `$49` tier and all monthly memberships remain presentation-only. No purchase, renewal, entitlement count, or unlimited-plan enforcement exists.
- The local backend repositories and local helper-to-catalog projection are process-memory only. Restarting the backend clears local test accounts, applications, bookings, and dynamic helper visibility.
- The journey script validates the real local HTTP flows and shared domain behavior. Automated mouse/touch UI driving is not configured for Compose Desktop, iOS, or Android, so platform-specific camera, permissions, biometrics, Keychain/Keystore runtime, SMS autofill, and layout/touch behavior still require device tests.
- Docker/PostGIS/Redis and Flyway migrations remain unvalidated on this Windows host because Docker is unavailable.
- Gradle 9.5 succeeds. Detekt 1.x emits one Gradle 10 API deprecation warning; a tested Detekt upgrade is required before moving to Gradle 10.

The former next step to implement Requests/Jobs is complete and superseded by the current exact next step below.

## 2026-07-20 real helper workspace and participant-chat correction

Completed and verified:

- Created a real approved-helper Requests/Jobs/Chats/Profile workspace with an explicit Requests refresh button.
- Verified that an approved helper created in the Helpers flow appears in customer search for ZIP `32539`, while unapproved helpers and all runtime seed fixtures remain excluded.
- Verified customer-to-helper direct messaging, helper reply, and booking-created shared conversations through the same chat records.
- Verified two customer booking requests appear after helper refresh, and helper acceptance advances the server-authoritative booking without changing its price.
- Verified editing every helper profile group, including ZIP `32539`, updates the customer-facing catalog projection.
- Verified PNG file bytes can be selected/uploaded/completed without asking the helper to type a URL. Invalid image signatures remain rejected.
- Removed fictional request cards and runtime fictional catalog people. Test fixtures remain isolated behind explicit test construction.

Commands and results:

| Command | Result |
| --- | --- |
| `.\gradlew.bat :backend:test` | First run exposed one signed-byte PNG validation defect; after correction the backend suite passed. Current backend suite: 64 tests, 0 failures/errors/skips. |
| `.\gradlew.bat :apps:helpers:compileKotlinIosSimulatorArm64 :apps:helpers:compileAndroidMain` | Initial iOS native-picker mapping errors were corrected; final command passed. |
| `.\gradlew.bat --write-verification-metadata sha256 :apps:helpers:generateProjectStructureMetadata` | Passed and added hashes for the official Android file-picker transitive artifacts reached by the new dependency. |
| `.\gradlew.bat ktlintFormat` | Applied mechanical Kotlin formatting; initially reported two unwrappable fixture lines, which were wrapped manually. |
| `.\gradlew.bat ktlintCheck detekt` | Passed after formatting and Detekt corrections. |
| `.\gradlew.bat build` | Passed in 64 seconds for the complete repository graph. Current discovered JVM total: 80 tests, 0 failures/errors/skips. |
| `.\gradlew.bat :apps:helpers:androidApp:assembleDebug :apps:customer:androidApp:assembleDebug :apps:helpers:compileKotlinIosSimulatorArm64 :apps:customer:compileKotlinIosSimulatorArm64 :backend:test ktlintCheck detekt` | Passed in 23 seconds; both Android APKs packaged, both iOS simulator source targets compiled, backend tests and quality gates passed. |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-local-journeys.ps1 -ZipCode 32539` | One run exposed a test-harness selection bug caused by omitted default JSON fields; no application pass was claimed for it. After selecting the explicitly read-only thread, a clean-backend run passed 118/118 checks with 0 failures. |
| `.\gradlew.bat build ktlintCheck detekt` (final source state) | Passed in 76 seconds after making the customer Message action navigate immediately; the complete repository build and quality gates remain clean. |

Current limitations:

- Local accounts, applications, catalog projections, bookings, and conversations remain process-memory data and reset when the backend restarts.
- The dynamic approved-helper catalog projection is verified with the local in-memory repository. The PostgreSQL application-to-catalog projection and end-to-end migration run still require a Docker/PostGIS-capable host.
- iOS source, including the document picker, compiles on Windows, but native framework linking, picker interaction, Keychain behavior, signing, simulator execution, and iPad testing require macOS/Xcode.
- Automated HTTP journeys cover business behavior, but no Compose mouse/touch automation is configured. Layout and native-picker UX still need platform-device tests.
- Helper accept/decline exists; provider quoting, the customer's payment authorization continuation, job execution/completion, and production notifications are not complete.

## 2026-07-26 Android Studio dependency-model import correction

Completed:

- Confirmed the Android Studio failures were caused entirely by missing checksums for optional source attachments: `*-sources.jar` and `gradle-9.5.0-src.zip`.
- Added narrowly scoped trusted-artifact rules for those non-build source attachments only.
- Kept strict verification enabled for metadata and all actual build dependencies.
- Verified the shared design-system, customer, and helper Kotlin IDEA import tasks.
- Reverified the complete repository build and quality gates.

Commands and results:

| Command | Result |
| --- | --- |
| `.\gradlew.bat :shared:designsystem:prepareKotlinIdeaImport :apps:customer:prepareKotlinIdeaImport :apps:helpers:prepareKotlinIdeaImport --console=plain` | First sandboxed attempt could not download Gradle because network access was denied; no import pass was claimed. Rerun with network/cache access passed in 14.6 seconds. |
| `.\gradlew.bat build ktlintCheck detekt --console=plain` | First sandboxed attempt hit the same Gradle distribution network block; no build pass was claimed. Rerun with network/cache access passed in 8.8 seconds: 976 actionable tasks, 55 executed, 921 up-to-date. |

Known limitation:

- Gradle 9.5 still reports plugin-ecosystem deprecations that will require attention before Gradle 10. This warning is separate from dependency verification and does not fail the current build.

Exact next step:

- Reopen the OneDrive repository in Android Studio with its bundled `jbr-21`, then use **File → Sync Project with Gradle Files**. The previously failing source attachments now match the narrow trusted-artifact rules.

## 2026-07-26 Android Studio local JVM pin and build recheck

Completed:

- Rechecked the Android Studio Kotlin IDEA import tasks, both Android debug APK tasks, and the full build/quality gate from this workspace.
- Confirmed there are no current Gradle source or Android packaging failures on the command line.
- Pinned the local Android Studio project metadata to `jbr-21`/JDK 21 so Studio uses the same Java level as the passing Gradle build.
- Added README instructions for opening the repository root, selecting `jbr-21`, syncing Gradle, and using the Customer or Helpers run configurations.

Commands and results:

| Command | Result |
| --- | --- |
| `.\gradlew.bat :shared:designsystem:prepareKotlinIdeaImport :apps:customer:prepareKotlinIdeaImport :apps:helpers:prepareKotlinIdeaImport --console=plain --stacktrace` | Passed in 3 seconds; Android Studio-style Kotlin project import tasks completed. |
| `.\gradlew.bat :apps:customer:androidApp:assembleDebug :apps:helpers:androidApp:assembleDebug --console=plain --stacktrace` | Passed in 3 seconds; both Android debug APKs assembled. |
| `.\gradlew.bat --version --console=plain` | Passed; Gradle 9.5.0 is launching on Java 21.0.8 and the daemon criteria require Java 21. |
| `.\gradlew.bat build ktlintCheck detekt --console=plain --stacktrace` | Passed in 14 seconds; complete build, Android lint, ktlint, Detekt, and current tests/checks completed. |
| `[xml](Get-Content -Raw .idea\gradle.xml) ...; 'XML OK'` | Passed; local Android Studio metadata and shared run configuration XML parse cleanly. |
| `.\gradlew.bat :shared:designsystem:prepareKotlinIdeaImport :apps:customer:prepareKotlinIdeaImport :apps:helpers:prepareKotlinIdeaImport --console=plain --stacktrace` (post-edit recheck) | Passed in 3 seconds; all tasks were up-to-date after pinning the local Studio JVM metadata. |

Known limitation:

- If Android Studio was previously opened with an older Gradle JDK or stale project model, it may still need to be closed and reopened before syncing. The backend must also be running separately for the Android app to sign in or reach local APIs from the emulator.

Exact next step:

- In Android Studio, open `C:\Users\Fuwa\Downloads\Digibuddy-local`, confirm Gradle JDK is `jbr-21`, run **File > Sync Project with Gradle Files**, then select `Digibuddy Customer` or `Digibuddy Helpers`.

## 2026-07-26 build-error triage from local workspace

Completed:

- Rechecked the repository from `C:\Users\Fuwa\Downloads\Digibuddy-local` after a report of widespread build errors.
- Could not reproduce a command-line Gradle build failure in this workspace.
- Verified Android Studio-style Kotlin IDEA import tasks still pass.
- No source changes were required for the verified command-line build path.

Commands and results:

| Command | Result |
| --- | --- |
| `.\gradlew.bat build --console=plain --stacktrace` | Passed in 5 seconds; 974 actionable tasks, 73 executed and 901 up-to-date. Both Android app build paths, shared/backend compilation, lint/check tasks, and current tests completed. |
| `.\gradlew.bat clean build ktlintCheck detekt --console=plain --stacktrace` | Passed in 21 seconds from a clean workspace build; 1012 actionable tasks, 627 executed, 342 from cache, and 43 up-to-date. Android lint reports were written for both apps. |
| `.\gradlew.bat :shared:designsystem:prepareKotlinIdeaImport :apps:customer:prepareKotlinIdeaImport :apps:helpers:prepareKotlinIdeaImport --console=plain --stacktrace` | Passed in 2 seconds; all checked Kotlin IDEA import tasks were up-to-date. |

Known limitation:

- Gradle still reports plugin deprecation warnings for future Gradle 10 compatibility, but those warnings did not fail the Gradle 9.5 build. If Android Studio still shows errors, the next useful artifact is the exact Gradle Sync or Build output from Studio because the command-line build is currently clean.

Exact next step:

- Reopen the repository root in Android Studio, confirm the Gradle JDK is `jbr-21`, run **File > Sync Project with Gradle Files**, and capture the first failing Gradle error if Studio still reports one.

## 2026-07-27 Railway backend verification metadata correction

Completed:

- Diagnosed the Railway backend build failure as Gradle dependency verification rejecting three Maven metadata artifacts resolved during the Linux/Railpack backend build.
- Added exact SHA-256 checksums for `com.google.guava:guava-parent:33.3.1-jre` POM and `org.junit:junit-bom` module metadata versions `5.10.2` and `5.11.0-M2`.
- Kept strict verification enabled; no executable JAR/AAR trust rule was broadened.
- Verified the dependency verification XML parses and the backend install distribution builds locally.

Commands and results:

| Command | Result |
| --- | --- |
| `.\gradlew.bat --write-verification-metadata sha256 :backend:installDist --console=plain --no-daemon` | Passed locally but did not reproduce the Railway/Linux-only missing metadata entries. |
| `Invoke-WebRequest ... .sha256` / raw artifact hashing for the three reported Maven metadata files | Passed after network approval; exact SHA-256 values were verified from Maven Central before editing metadata. |
| `[xml](Get-Content -Raw gradle\verification-metadata.xml) ...` | Passed; dependency verification metadata XML parses cleanly. |
| `.\gradlew.bat :backend:installDist --console=plain --no-daemon` | Passed in 12 seconds; backend distribution remains buildable locally. |

Known limitation:

- The Railway redeploy still needs to be run from the local workspace so the updated `gradle/verification-metadata.xml` is uploaded to the `digibuddy-backend` service.

Exact next step:

- Run `railway.cmd up --service digibuddy-backend` from `C:\Users\Fuwa\Downloads\Digibuddy-local`, then check the `digibuddy-backend` build logs. If build succeeds, generate/open its public domain and test `/health`.

## Required credentials

No credentials are required for local authentication tests, the development OTP flow, or `/health`.

- Local PostGIS uses the disposable defaults from `.env.example`; copy them only to ignored `.env` and never reuse them in hosted environments.
- A local Apple development team is required for a signed device build; simulator compilation does not need production signing credentials.
- Hosted authentication requires `AUTH_TOKEN_PEPPER` and a distinct `AUTH_IDENTIFIER_KEY` from a secret manager, plus PostgreSQL credentials.
- Twilio Verify requires server-side `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and `TWILIO_VERIFY_SERVICE_SID`. They are optional and unused by local tests.
- Stripe, APNs, and S3 sandbox credentials remain optional and unused by their development placeholders.
- Production credentials, private keys, payment secrets, APNs signing keys, and object-store secrets must remain server-side in a managed secret store.

## Next phase / exact next step

On a Mac with Xcode, run both iOS hosts against the same local backend and repeat the `32539` journey on an iPhone simulator and the available iPad: approve one helper, edit the helper profile and ZIP, choose a real profile image, find/message/request that helper from the customer app, refresh Requests, accept, and exchange replies. Record native picker, Keychain, layout, and lifecycle results here.

After that device gate, persist the existing helper application-to-catalog, booking, and participant-chat projections in PostgreSQL and add provider quote/customer payment continuation to the existing server state machine. Do not introduce a second backend, database, identity system, booking model, chat model, or payment record.

After that connected booking gate, continue H2 public helper-profile editing on the existing allowlisted public snapshot: photo/banner crop and compression, upload cancel/retry/remove, presigned production boundaries, moderation status, approved skill/category validation, and Preview as Customer. Keep legal name, phone, home location, identity, bank, and tax data private; keep ratings, reviews, completed jobs, verification, approval, response time, and ranking server-owned.

On a Docker-capable development host, copy `.env.example` to the ignored `.env`, change the local pepper/key values, then run the following before starting another feature phase:

```shell
docker compose --env-file .env -f infrastructure/docker-compose.yml config
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d postgres redis
docker compose --env-file .env -f infrastructure/docker-compose.yml --profile migration run --rm migrate
```

Start the backend with `AUTH_REPOSITORY=postgresql`, execute one local phone signup, refresh, logout, and rejected-token flow, and record the results here. Then, on macOS with Xcode, build and run the iOS host and verify Keychain persistence/removal plus SMS code autofill. Do not begin bookings, chat, or payments until these authentication integration gates are closed.

### Phase 3 exact next step

Implement and integration-test the PostgreSQL `CustomerProfileRepository` against Flyway V3, then bind iOS PhotosUI/camera crop-compression and permission adapters into `CustomerPhotoActions`. Run the Xcode host on an iPhone simulator and a physical device before beginning marketplace discovery. Do not begin bookings, chat, or payments.

### Phase 4 exact next step

On a Docker/PostGIS-capable host, run Flyway through V5 and execute authenticated search smoke tests for ZIP `60601`, an exact-ZIP in-person match, a radius match, and a remote-only match using `AUTH_REPOSITORY=postgresql`. Capture `EXPLAIN (ANALYZE, BUFFERS)` for the catalog query to confirm the geography GIST indexes are used before adding the customer search UI. Do not begin bookings, chat, or payments.
