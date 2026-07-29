# Digibuddy

Digibuddy is an iOS-first Kotlin Multiplatform marketplace foundation with an Android host retained for future use and one Ktor backend intended to serve both customer and helper applications.

The repository now includes phone-first authentication, customer onboarding/profile settings, saved helper applications, helper discovery, server-authoritative booking workflows, booking-linked chat, a development payment flow, notification registration, and polished shared customer/helper screens. The Windows desktop previews run the same Compose UI used by the mobile hosts.

This is a substantial development preview, not an App Store-ready release. PostgreSQL persistence for the newest marketplace services, native MapKit rendering, real Stripe/APNs transports, provider sandbox certification, and macOS/Xcode signing remain release work.

## Repository map

```text
apps/customer/                  Shared customer KMP/Compose application
apps/customer/androidApp/       Thin Android application host required by AGP 9
apps/customer/iosApp/           SwiftUI/Xcode host for the shared iOS framework
apps/helpers/                   Shared Digibuddy Helpers KMP/Compose application
apps/helpers/androidApp/        Thin Helpers Android application host
apps/helpers/iosApp/            SwiftUI/Xcode host for the Helpers framework
shared/contracts/               Serialized client/server contracts
shared/core/                    Platform-neutral domain foundations
shared/designsystem/            Accessible Compose theme foundations
shared/networking/              Ktor client and local endpoint configuration
shared/database/                SQLDelight local cache infrastructure
shared/authentication/          Shared authentication state, screens, and secure-storage contract
shared/profile/                 Shared customer onboarding, profile, and settings presentation
shared/helper-onboarding/       Saved helper onboarding, lifecycle routing, and status presentation
shared/helper-dashboard/        Four-tab provider workspace presentation
backend/                        One Ktor JVM backend
infrastructure/                 Local PostGIS, Redis, and migration orchestration
docs/                           Architecture, status, plan, security, and ADRs
```

The separate Android host modules are required by AGP 9; see [ADR 0001](docs/decisions/0001-agp9-platform-host-split.md). The Helpers client reuses the same backend/domain boundaries as recorded in [ADR 0005](docs/decisions/0005-helper-client-shared-platform.md), and its persisted application lifecycle is recorded in [ADR 0006](docs/decisions/0006-helper-application-lifecycle-and-role-provisioning.md).

## Toolchain

- JDK 21
- Gradle Wrapper 9.5.0 with distribution checksum verification
- Kotlin/KMP 2.4.10
- Compose Multiplatform 1.11.1 and Compose Material 3 1.9.0
- Ktor 3.5.1
- SQLDelight 2.3.2
- Koin 4.2.1
- Android compile/target SDK 35, minimum SDK 24
- iOS 17 minimum deployment target

Dependency versions are centralized in `gradle/libs.versions.toml`; resolved artifact hashes are recorded in `gradle/verification-metadata.xml`.

## Prerequisites

- JDK 21 and Git
- Android Studio plus Android SDK 35 for Android work
- macOS with Xcode 26.4 for iOS framework linking, simulator execution, signing, and archives
- Docker with Compose for PostgreSQL/PostGIS, Redis, and migration validation

No standalone Gradle or Kotlin installation is required. Do not place production or shared credentials in this repository.

## Android Studio

Open the repository root, not an individual app folder. In Android Studio, set **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK** to the bundled `jbr-21` runtime, then run **File > Sync Project with Gradle Files**.

The shared run configurations are:

- `Digibuddy Customer`
- `Digibuddy Helpers`

Both Android apps build from the thin AGP host modules under `apps/*/androidApp`. If the IDE model looks stale after changing JDKs, close Android Studio, reopen this root folder, and sync again.

## Local setup

Create an ignored local environment file:

```powershell
Copy-Item .env.example .env
```

On macOS/Linux, use `cp .env.example .env` instead. The checked-in defaults are disposable local-development values only.

Start the local services and run Flyway migrations:

```shell
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d postgres redis
docker compose --env-file .env -f infrastructure/docker-compose.yml --profile migration run --rm migrate
```

Start the backend with the safe local authentication adapters:

```powershell
.\gradlew.bat :backend:run
```

Then verify `GET /health`:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/health
```

The Android emulator uses `http://10.0.2.2:8080`; iOS and JVM local development use `http://127.0.0.1:8080`.

### Windows development preview

After starting the backend, launch the shared customer UI directly on Windows without Android Studio or an emulator:

```powershell
.\gradlew.bat :apps:customer:run
```

This development-only, phone-sized desktop window uses the same shared Compose screens and local API clients as the mobile hosts. It keeps refresh tokens in memory and does not emulate mobile-only integrations such as Keychain/Keystore, camera/photo selection, permissions, biometrics, push notifications, or SMS autofill.

Launch the separate provider-facing preview with:

```powershell
.\gradlew.bat :apps:helpers:run
```

The Helpers preview restores/signs in through the same account system, then reads `/api/v1/helper/startup` to route across all nine helper states. New local accounts use a guided, independently saved application flow. After a complete application is submitted, the desktop build offers a clearly labeled **Approve for local testing** action. It performs a real local-only approval and makes the helper searchable by the customer preview until the in-memory backend restarts. Android and iOS do not expose this development control. Approved helpers can refresh real customer requests, accept or decline them, exchange messages with the customer, edit their public/application information, change ZIP, and choose a profile photo from the native file picker.

Customer pricing is set by Digibuddy: $29 quick remote help, $49 Standard Help for 30–60 minutes, and $79 for an in-home visit. The Home screen also previews $9.99/10-issue, $19.99/30-issue, and $99.99/unlimited monthly memberships. Membership checkout and recurring billing are not active yet.

### Local development OTP code

With `DIGIBUDDY_ENVIRONMENT=local-development`, `AUTH_REPOSITORY=memory`, and `AUTH_OTP_PROVIDER=development`, every verification attempt gets a new random six-digit code. It is not hard-coded. The local-only backend response includes the code in the `developmentCode` field, and the customer screen displays it as **Local development code**.

For example, start an attempt with:

```powershell
$body = @{ phoneNumber = "+13125550199"; defaultRegion = "US" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/v1/auth/phone/verifications -ContentType application/json -Body $body
```

Read `developmentCode` from that response and enter it in the verification screen. The development adapter refuses to start outside `local-development`; Twilio Verify responses never include a code.

### Production-like authentication configuration

Hosted environments must set `AUTH_REPOSITORY=postgresql` and `AUTH_OTP_PROVIDER=twilio-verify`. They must also supply unique secret values for `AUTH_TOKEN_PEPPER` and `AUTH_IDENTIFIER_KEY`, plus `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and `TWILIO_VERIFY_SERVICE_SID` from a server-side secret manager. Startup rejects the in-memory repository, development OTP adapter, and checked-in local secret placeholders outside local development.

## Build and test

Windows commands:

```powershell
.\gradlew.bat build
.\gradlew.bat :shared:core:jvmTest :shared:contracts:jvmTest :shared:database:jvmTest :shared:networking:jvmTest
.\gradlew.bat :backend:test
.\gradlew.bat :shared:authentication:jvmTest
.\gradlew.bat :apps:customer:androidApp:compileDebugKotlin
.\gradlew.bat :apps:helpers:androidApp:compileDebugKotlin
.\gradlew.bat :shared:helper-onboarding:jvmTest :shared:helper-dashboard:jvmTest
.\gradlew.bat ktlintCheck detekt
```

With the backend running in its default local-development mode, exercise the connected customer/helper journeys with realistic fictional data for Crestview ZIP `32539`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-local-journeys.ps1 -ZipCode 32539
```

The script creates only disposable in-memory development accounts. It verifies phone signup, customer onboarding and settings, helper onboarding/review/local approval, customer discovery, request refresh/acceptance, two-party customer/helper chat, editable helper profile projection in ZIP `32539`, file-byte profile upload, server-authoritative `$29` remote and `$79` in-home booking prices, booking privacy/idempotency, the read-only welcome conversation and support number, email/password plus SMS login, refresh-token reuse detection, data export, account deletion, and current/all-device logout. Restart the in-memory backend before repeating the script with its default fictional phone numbers.

On macOS/Linux, replace `.\gradlew.bat` with `./gradlew`.

To build Android:

```powershell
.\gradlew.bat :apps:customer:androidApp:assembleDebug
.\gradlew.bat :apps:helpers:androidApp:assembleDebug
```

To build iOS, open `apps/customer/iosApp/Digibuddy.xcodeproj` in Xcode. The shared scheme runs the repository Gradle Wrapper to build and embed `DigibuddyCustomer`. Change the placeholder bundle identifier in `apps/customer/iosApp/Configuration/BundleIdentifiers.xcconfig` and select a local development team when signing is needed.

For Digibuddy Helpers, open `apps/helpers/iosApp/DigibuddyHelpers.xcodeproj`. It embeds `DigibuddyHelpers` and keeps the placeholder `com.digibuddy.helpers` identifier in its own xcconfig.

## Development adapters and secrets

Twilio Verify is implemented as a server-only production OTP adapter; the credentials remain optional for local tests. Profile photos have a validated process-memory local adapter and a hosted presigned-upload interface; the S3-compatible signer remains a deployment adapter, not a completed production integration. Stripe and APNs now have server-side provider boundaries, configuration validation, and local development adapters. Their outbound production transports remain disabled until real provider accounts and hosted secret management exist. Provider secrets belong only in ignored local configuration or a managed server-side secret store. Never embed API secrets in either mobile app.

## Customer profile lifecycle

After phone verification, the customer sees a short onboarding flow. First name, last name, and five-digit ZIP code are required; photo, location, notifications, and technology interests can be skipped. Email/password is added later under account settings and is never required for onboarding.

The authenticated customer API is under `/api/v1/customer`. It covers profile/settings updates, saved addresses, security activity, photo-upload grants, data-export requests, and deletion requests. Deletion requires a phone-authenticated session created within the last ten minutes, records a soft-deletion request, changes account status, and revokes all sessions. Active bookings prevent deletion until a documented fulfillment/cancellation policy is completed.

## Helper authentication and onboarding

Helper application APIs reuse the normal access token:

- `GET /api/v1/helper/startup`
- `GET /api/v1/helper/application`
- `PUT /api/v1/helper/application/steps/{step}`
- `POST /api/v1/helper/application/submit`
- `POST /api/v1/helper/application/pause`
- `POST /api/v1/helper/application/resume`
- `PUT /api/v1/helper/application/profile`
- `POST /api/v1/helper/application/profile/photo/uploads`
- `POST /api/v1/helper/application/profile/photo/complete`
- `DELETE /api/v1/helper/application/profile/photo`

Every onboarding step is saved separately. Public/private and required/optional classifications come from the server. There is intentionally no public approval endpoint: staff review remains a server-side boundary, and only approval grants the shared `HELPER` role. The payout step is explicitly a development placeholder and never collects bank or tax details.

## Helper catalog API

Catalog reads require a customer access token:

- `GET /api/v1/customer/helpers/search?zipCode=60601`
- `GET /api/v1/customer/helper-filters`
- `GET /api/v1/customer/service-categories`
- `GET /api/v1/customer/helpers/{helperId}`
- `GET /api/v1/customer/helpers/{helperId}/availability`

Search supports category, skill, availability window, minimum rating, maximum starting price, language, remote/in-person mode, verified-only, sort, page, and page-size parameters. PostgreSQL uses indexed PostGIS geography points and `ST_DWithin` for radius preselection. The runtime catalog displays only eligible approved helper accounts. Historical V5 seed rows are marked as seed data and excluded from public search; fictional catalog entries are enabled only by explicit tests.

Helper profiles also expose public portfolio and review summaries. The customer UI provides list/map-style discovery, filters, sort choices, recent views, helper details, availability, and a booking entry point. The common preview map uses approximate service-area presentation; native MapKit rendering remains iOS platform work.

## Marketplace development preview

After local phone verification, the customer app opens a five-tab shell: Home, Find, Bookings, Chats, and Profile. Find searches the authenticated helper catalog. Booking creation uses an idempotent server command and renders canonical status/history. The approved Helpers app opens Requests, Jobs, Chats, and Profile; Requests has a manual refresh action and reads the same booking records. Customer/helper chat uses shared participant-authorized conversations with message idempotency and ordered HTTP synchronization. The payment screen uses a clearly labeled local adapter and never collects raw card data.

Backend additions include Flyway migrations V6-V9 for bookings, chat/realtime events, payment ledger/receipts, and notification devices/preferences/deliveries. Runtime implementations for these newest areas are process-memory development repositories until the PostgreSQL repositories and integration suites are completed.

## Logo handling

`DigibuddyLogo.png` is the protected source logo. Resource copies preserve its exact bytes and are displayed with aspect-fit behavior so the image is not stretched. No app icons or branded splash artwork were generated because the rectangular source is not technically appropriate for square platform icons; those should be designed later as padded derivatives under different names.

See [Architecture](docs/ARCHITECTURE.md), [Project status](docs/PROJECT_STATUS.md), [Development plan](docs/DEVELOPMENT_PLAN.md), [Security model](docs/SECURITY_MODEL.md), [Deployment](docs/DEPLOYMENT.md), [Operations](docs/OPERATIONS.md), [Privacy checklist](docs/PRIVACY_CHECKLIST.md), and [App Store checklist](docs/APP_STORE_CHECKLIST.md).
