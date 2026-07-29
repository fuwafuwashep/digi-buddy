# Digibuddy App Store Checklist

**Status:** iOS release work remains

## Product and account

- [ ] Finalize app name, subtitle, category, age rating, support URL, marketing URL, privacy-policy URL, and bundle identifier.
- [ ] Configure App Store Connect roles, agreements, banking/tax requirements if marketplace payments require them, and production APNs.
- [ ] Provide review credentials/instructions using a safe test account and explain phone OTP, helper fixtures, booking, chat, and payment sandbox behavior.
- [ ] Make account deletion discoverable in-app and ensure the backend workflow is operational.

## Build and signing

- [ ] Build/archive on supported macOS/Xcode with the release scheme and selected team.
- [ ] Replace placeholder icons/launch assets with purpose-designed, licensed, non-distorted derivatives; preserve the root source logo.
- [ ] Set version/build numbers, distribution certificates/profiles, entitlements, universal links, Keychain groups, APNs environment, and export-compliance answers.
- [ ] Verify release builds contain no development adapters, sample codes, debug menus, localhost endpoints, test keys, or verbose personal-data logging.

## Experience and accessibility

- [ ] Test VoiceOver labels/order, Dynamic Type through accessibility sizes, contrast, reduced motion, touch targets, keyboard behavior, error recovery, and plain language on physical iPhones.
- [ ] Test offline/reconnect, interrupted OTP, expired sessions, booking conflict, chat retry, payment cancellation, provider delay, and notification/deep-link navigation.
- [ ] Integrate native MapKit with approximate public pins and clear list-view parity before claiming the map feature complete.

## Privacy and platform policy

- [ ] Complete `PRIVACY_CHECKLIST.md`, privacy manifest, nutrition labels, tracking declarations, permission purpose strings, and data-deletion/support procedures.
- [ ] Confirm payment design complies with current marketplace/App Store rules and Stripe's connected-account/KYC model; Digibuddy must never collect raw card data.
- [ ] Review user-generated content requirements: reporting, blocking, moderation, contact method, response SLA, and objectionable-content handling.
- [ ] Validate every third-party SDK, required-reason API, cryptography/export answer, and account/sign-in policy.

## Release evidence

- [ ] Pass unit/integration/UI tests, provider sandboxes, migration/restore exercise, dependency/secret scans, penetration test, privacy review, and incident runbook exercise.
- [ ] Capture screenshots/previews from the signed iOS build in all required device sizes and ensure they represent real functionality.
- [ ] Use phased release with crash, latency, auth, booking, chat, payment, and notification monitoring plus a documented rollback/kill-switch plan.
