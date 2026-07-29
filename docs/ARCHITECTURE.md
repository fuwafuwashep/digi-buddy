# Digibuddy Architecture

**Status:** shared-platform baseline accepted; helper authentication/onboarding lifecycle implemented

**Decision date:** 2026-07-18

**Scope:** foundation, shared identity/domain, customer marketplace preview, and Digibuddy Helpers through H1

## Goals

Digibuddy will provide an iOS-first customer experience, retain an Android target for later product work, and eventually support a separate Digibuddy Helpers app. Both apps must use one backend and the same canonical booking, identity, messaging, and payment state.

The starting shape is a modular monorepo with Kotlin Multiplatform mobile code, Compose Multiplatform UI, and a Kotlin/JVM Ktor backend. The first backend deployment should be a modular monolith: operationally simple, transaction-friendly, and internally divided by bounded context so services can be extracted later only if scale or team ownership justifies it.

## Repository and Gradle modules

```text
Digibuddy/
├── apps/
│   └── customer/             # :apps:customer KMP app and future iosApp Xcode shell
├── shared/
│   ├── core/                 # :shared:core primitives, errors, clocks, result types
│   ├── contracts/            # :shared:contracts versioned API DTOs and events
│   ├── designsystem/         # :shared:designsystem accessible Compose tokens/components
│   ├── networking/           # :shared:networking Ktor client, transport, API adapters
│   ├── database/             # :shared:database SQLDelight cache and repositories
│   ├── authentication/       # :shared:authentication client auth domain/presentation later
│   ├── bookings/             # :shared:bookings booking domain/presentation later
│   └── chat/                 # :shared:chat messaging domain/presentation later
├── backend/                  # :backend single Ktor JVM service
├── infrastructure/           # local/hosted deployment definitions and migrations support
├── docs/
├── gradle/                   # version catalog and wrapper files after scaffolding
├── DigibuddyLogo.png
└── README.md
```

### Explained layout changes

The requested top-level layout is preserved. One module, `shared/contracts`, is added because clients and server need common serialized request/response/event types without making the server depend on the mobile Ktor client or leaking backend persistence models. This boundary also makes API compatibility tests possible.

`apps/customer` is planned as the Gradle KMP application module rather than adding another redundant `composeApp` directory. A thin `apps/customer/iosApp` Xcode shell will be generated inside it because Xcode project metadata, signing, lifecycle integration, and native resources are not Gradle modules. This is a nested platform requirement, not a change to the requested top-level structure.

AGP 9 requires the Android application entry point to be a separate host module because its built-in Kotlin support cannot be combined with the Kotlin Multiplatform plugin. `:apps:customer` therefore remains the shared KMP/Compose module and `:apps:customer:androidApp` is a thin Android host. This recorded adjustment is defined in [ADR 0001](decisions/0001-agp9-platform-host-split.md).

The helper client is implemented under `apps/helpers`, using the plural product name to distinguish it from the backend's singular helper actor route. It mirrors the customer AGP 9 split: `:apps:helpers` owns shared KMP/Compose code and `:apps:helpers:androidApp` is a thin host. Its SwiftUI host is not a Gradle module. Helper lifecycle and dashboard presentation live in `:shared:helper-onboarding` and `:shared:helper-dashboard`. See [ADR 0005](decisions/0005-helper-client-shared-platform.md).

## Dependency direction

```text
apps/customer
  -> shared/designsystem
  -> shared/authentication, shared/bookings, shared/chat
  -> shared/networking, shared/database

shared feature modules
  -> shared/core, shared/contracts
  -> ports implemented by networking/database where appropriate

shared/networking, shared/database, shared/designsystem
  -> shared/core, shared/contracts

backend
  -> shared/contracts
  -> backend-only domain, application, persistence, and integration packages
```

`apps/helpers` follows the same dependency direction as the customer client: it consumes the shared design system, authentication, contracts, networking, and helper presentation modules. It does not depend on backend implementation packages and does not define alternate booking, chat, or payment DTOs.

`shared/core` and `shared/contracts` must not depend on UI, Ktor client engines, SQL drivers, or backend persistence. Feature modules should own use cases and state models; infrastructure modules implement their ports. Platform source sets provide secure storage, device APIs, and native integration.

## Client architecture

Both apps use a unidirectional presentation flow: immutable screen state, explicit user intents, coroutine-driven use cases, and observable state exposed to Compose. Business rules stay outside composables. UI state is not a server state machine.

The Helpers startup composition restores the shared authentication session, then calls the additive `/api/v1/helper/startup` endpoint. H1 adds nine precise helper states while retaining the H0 `onboardingStatus` field as a compatibility projection. Draft, review, changes-requested, paused, suspended, and rejected states stay outside the Requests/Jobs/Chats/Profile shell; only `APPROVED` enters it.

Helper onboarding is a server-backed workflow in `:shared:helper-onboarding`. Each step is saved independently through `/api/v1/helper/application/steps/{step}` and can be resumed after sign-out. Composables render immutable application state and explicit save/submit intents; they do not infer approval. Fields carry visible public/private and required/optional labels. See [ADR 0006](decisions/0006-helper-application-lifecycle-and-role-provisioning.md).

The shared target set will be:

- `commonMain` and `commonTest`
- `iosArm64` for physical iOS devices
- `iosSimulatorArm64` for Apple silicon simulators
- `android` through Google's Kotlin Multiplatform Android library plugin for shared modules
- An Android application host in `apps/customer/androidApp`, kept compiling but not given feature parity priority until the Android product phase

Compose Multiplatform 1.11.1 supports iOS 14 and newer. The foundation Xcode host targets iOS 17 to reduce compatibility surface while retaining broad device coverage. Lowering that target requires an explicit product decision and compatibility verification.

SQLDelight is the mobile local database. It stores cacheable catalog, booking summary, and message synchronization data when those features exist. It must not be treated as authoritative, and credentials/tokens must use Keychain on iOS and Keystore-backed secure storage on Android instead.

Koin supplies dependency injection and composition roots. Keep constructor injection in domain/application code so most code remains testable without starting a DI container.

## Backend architecture

One Ktor JVM service will serve both Digibuddy customer and helper clients. Its public API will distinguish actors through authenticated identity, roles, resource ownership, and policy—not separate deployments or duplicated databases.

The backend should begin as one deployable with internal modules/packages for:

- identity and access
- customer/helper profiles
- service catalog and discovery
- availability
- bookings
- chat and delivery state
- payments and provider webhooks
- notifications
- administration, audit, and support

HTTP/JSON is the command/query and synchronization transport. Authenticated Ktor WebSockets provide live chat events, while resumable HTTP pagination remains the reliability fallback. Public customer routes use the `/api/v1` prefix.

PostgreSQL is recommended as the eventual server system of record. The server persistence access library and migration tool remain an explicit Phase 1 decision because they are independent of the requested mobile local database choice. This prevents prematurely coupling domain design to an ORM.

### Authentication architecture

Phone numbers normalized to E.164 are the primary account identity. The backend owns normalization, OTP attempts, account creation, Argon2id email-password credentials, device records, and session state. Access tokens are opaque, server-tracked, and short lived. Refresh tokens are opaque, stored only as hashes on the backend, rotated on every use, and retained as hashes after rotation so reuse revokes the affected user's sessions.

The customer client keeps access tokens in memory. Its refresh-token storage port is implemented with iOS Keychain and Android Keystore AES/GCM; Android preferences contain only ciphertext and an initialization vector. Email/password is optional and can be added only from an authenticated phone account. It is never a single-factor login: successful password verification creates an SMS challenge and no session is issued until that challenge succeeds.

Local development uses an in-memory repository and random, per-attempt OTP adapter. Production-like environments are configuration-gated to PostgreSQL and Twilio Verify. Database access is parameterized JDBC through HikariCP and schema changes use Flyway. See [ADR 0002](decisions/0002-phone-authentication-and-session-security.md).

Helper application data uses the same authenticated identity but separate V10 records. Private step payloads may be returned only to their owning account. Public helper presentation uses an explicit allowlist. Approval is a server review transition that atomically records the event and grants `HELPER` in PostgreSQL; service activation and paid-request eligibility require both the role and `APPROVED` application status.

### Authority and state

- The backend alone validates and commits booking transitions.
- The backend alone derives payment status from provider-confirmed operations and verified webhooks.
- Clients send commands with idempotency keys and render the state returned by the server.
- Role checks are server-side for every protected resource.
- Every consequential state transition produces an audit record without storing secrets or raw payment data.

Customer prices are platform-owned rather than helper-owned. The canonical shared schedule is $29 quick remote help, $49 Standard Help (30–60 minutes), and a $79 in-home visit; booking totals are derived by the backend instead of trusting a client or helper amount. The displayed $9.99/10-issue, $19.99/30-issue, and $99.99/unlimited monthly memberships are product-catalog previews until recurring billing and server-side entitlements are implemented. See [ADR 0007](decisions/0007-platform-pricing-and-local-helper-projection.md).

### Helper catalog and geographic search

Helper discovery is a bounded context inside the single backend. Network contracts expose allowlisted public helper summaries rather than persistence or identity records. Every search first requires an active account, approved helper status, catalog visibility, accepting-new-customers availability, and at least one active service.

ZIP centroids and helper service origins are PostgreSQL/PostGIS `geography(Point, 4326)` values. Exact service ZIP coverage is authoritative for that ZIP; service-radius coverage uses indexed `ST_DWithin` measurements in meters. Remote services match independently of location. Results use deterministic UUID tie-breakers for stable pagination. See [ADR 0004](decisions/0004-helper-catalog-geographic-search.md).

### Connected helper workspace and conversations

The approved Helpers workspace reads the canonical booking and chat services; it does not maintain provider-only copies. Customer booking commands contain a public catalog helper ID. The backend resolves that ID to the approved helper account and stores the private account ID on the booking. Only the customer and assigned helper can read or act on that booking. Helper acceptance advances the server-owned state and cannot alter the platform price.

Customer-to-helper and booking-linked conversations likewise contain both authenticated participant IDs. Either participant can list, open, and send in the same conversation. The Digibuddy welcome conversation is the only intentionally read-only thread. See [ADR 0008](decisions/0008-real-helper-workspace-and-participant-chat.md).

The local runtime catalog starts empty and shows only approved accounts created through the Helpers flow. Historical migration fixtures are marked `seed_data` and excluded from public PostgreSQL queries; in-memory fictional helpers remain available only when a test opts in. The Helpers profile edits the existing application/public projection, including ZIP, services, skills, languages, availability, experience, biography, certifications, and portfolio links. Profile photos cross the shared API as validated file bytes selected by native platform pickers, never as user-entered URLs.

## Stable version baseline

This baseline was selected on 2026-07-16 from stable upstream releases and official compatibility ranges. Versions will be centralized in `gradle/libs.versions.toml` during scaffolding.

| Component | Selected version | Reason |
| --- | ---: | --- |
| JDK toolchain | 21 | Installed LTS toolchain; supported by the selected Gradle line |
| Kotlin | 2.4.10 | Current stable bug-fix Kotlin release |
| Kotlin Multiplatform plugin | 2.4.10 | KMP plugin version is the Kotlin version; supports Gradle through 9.5.0, AGP through 9.1.0, and Xcode 26.4 |
| Compose Compiler plugin | 2.4.10 | Must match the Kotlin plugin version |
| Compose Multiplatform | 1.11.1 | Current stable Compose Multiplatform release; compatible with current stable Kotlin |
| Compose Material 3 | 1.9.0 | Stable independently versioned Material 3 artifact selected by the Compose line |
| Ktor client/server | 3.5.1 | Stable patch release including Kotlin 2.4 compiler compatibility fixes |
| Gradle Wrapper | 9.5.0 | Newest Gradle inside Kotlin/KMP 2.4.10's documented supported range; Java 21 compatible |
| Android Gradle Plugin | 9.1.0 | Upper stable AGP version documented for Kotlin/KMP 2.4.x; validate with installed Studio during scaffolding |
| kotlinx.serialization | 1.11.0 | Current stable multiplatform serialization runtime |
| kotlinx.coroutines | 1.11.0 | Current stable multiplatform coroutine runtime |
| SQLDelight | 2.3.2 | Current stable typesafe KMP local database library |
| Koin | 4.2.1 | Current stable lightweight Kotlin/KMP DI release |

Primary references:

- [Kotlin releases](https://kotlinlang.org/docs/releases.html)
- [Kotlin Multiplatform compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Compose Multiplatform compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Ktor releases](https://ktor.io/docs/releases.html)
- [Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
- [kotlinx.serialization releases](https://github.com/Kotlin/kotlinx.serialization/releases)
- [kotlinx.coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases)
- [SQLDelight releases](https://github.com/sqldelight/sqldelight/releases)
- [Koin releases](https://github.com/InsertKoinIO/koin/releases)

The KMP/Gradle/AGP and Compose/Kotlin combinations are explicitly documented upstream. The complete Gradle graph now builds on Windows, Android and Kotlin/Native simulator source compilation pass, and dependency hashes are checked in. Xcode framework linking and app execution still require verification on macOS.

## Quality strategy

- Common unit tests for domain rules, reducers, serialization, and repository behavior
- Platform tests for secure storage, database drivers, lifecycle, and network engines
- Backend unit and integration tests against an isolated PostgreSQL instance
- Contract compatibility tests shared across clients and server
- Accessibility checks for semantics, Dynamic Type/font scaling, contrast, focus order, touch targets, and simple language
- End-to-end tests for critical state transitions after features exist
- CI on macOS for iOS and on Linux for backend/Android/common checks

## Architecture decisions still open

Before implementation, record decisions for:

1. iOS minimum deployment target (recommendation: iOS 17).
2. Backend persistence access library and database migration tool.
3. API versioning and error envelope.
4. Account recovery beyond a signed-in phone session and stronger factors for privileged roles.
5. Hosting, secrets manager, observability, and notification providers.
6. Payment provider and marketplace funds-flow/compliance model.
7. Data retention, regional/privacy obligations, and support access policy.

None of these open decisions authorizes feature implementation. Material changes to the accepted baseline require an ADR rather than an unrecorded replacement.
