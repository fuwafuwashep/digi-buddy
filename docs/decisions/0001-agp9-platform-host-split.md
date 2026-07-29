# ADR 0001: Split platform hosts from the customer KMP library

- **Status:** Accepted
- **Date:** 2026-07-16
- **Decision owners:** Digibuddy architecture

## Context

The accepted architecture keeps `:apps:customer` as the iOS-first Kotlin Multiplatform and Compose module. Android Gradle
Plugin 9 enables built-in Kotlin and no longer allows `org.jetbrains.kotlin.multiplatform` to be combined with
`com.android.application`. Google's supported KMP plugin produces an Android library, not an Android application.

## Decision

Keep `:apps:customer` as the shared KMP/Compose UI library with Android and iOS targets. Add two thin platform hosts:

- `:apps:customer:androidApp` applies `com.android.application` and contains only the Android entry point and platform
  application configuration.
- `apps/customer/iosApp` is an Xcode project containing only the SwiftUI/UIKit host, signing configuration, and the build
  phase that embeds the Kotlin framework.

The application ID and initial iOS bundle ID remain `com.digibuddy.customer`. The iOS value is isolated in
`Configuration/BundleIdentifiers.xcconfig` so it can be changed without editing project internals.

## Consequences

- Shared UI and logic remain in one KMP module and are reused by both hosts.
- The Android host is a necessary nested Gradle module, not a separate product or backend.
- Platform hosts must stay thin; business rules and reusable UI do not move into them.
- Android and iOS can be compiled independently on their supported hosts.

## Alternatives rejected

- Disabling AGP 9 built-in Kotlin and using legacy APIs was rejected because those APIs are scheduled for removal in AGP
  10 and would create immediate migration debt.
- Downgrading AGP was rejected because the accepted version baseline already supports the new KMP Android library plugin.
