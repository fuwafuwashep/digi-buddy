# Digibuddy Privacy Checklist

**Status:** product/legal review required before real customer data

## Data inventory and purpose

- [ ] Inventory phone, optional email, profile, ZIP/address, preferences, device/session, helper discovery, booking, chat, photo, payment-token metadata, notification token, audit, and support data.
- [ ] Record purpose, legal basis, controller/processor roles, recipients, region, retention, deletion behavior, and whether each field appears in logs/analytics/backups.
- [ ] Confirm the helper-facing model never exposes customer phone/email or precise address before an authorized booking stage.
- [ ] Confirm public helper contracts contain only approved display fields and approximate service areas.
- [ ] Minimize notification payloads and telemetry; exclude chat bodies, exact addresses, access tokens, and payment facts.

## User controls

- [x] Optional location, notification, photo, and technology-interest onboarding steps are skippable.
- [x] Permission explanations are separate from platform prompts.
- [x] Data-export and deletion-request foundations exist.
- [ ] Define verified export identity, format, secure delivery, SLA, and abuse protection.
- [ ] Define erasure/retention behavior for backups, audit, fraud, disputes, chats, receipts, active bookings, and legal holds.
- [ ] Publish reviewed privacy notice, terms, support contact, and jurisdiction-specific disclosures.

## Vendors and transfers

- [ ] Complete agreements and security/privacy reviews for hosting, Twilio, Stripe, APNs, maps/geocoding, object storage, observability, email, and support tools.
- [ ] Record subprocessors and international transfer mechanisms.
- [ ] Configure vendor retention, training, analytics, and support access to the minimum available.

## Mobile privacy

- [ ] Add accurate iOS privacy manifest and required-reason API declarations after final SDK selection.
- [ ] Ensure App Store privacy answers exactly match runtime collection and linked third-party SDK behavior.
- [ ] Request camera/photos/location/notifications only at the relevant moment with plain-language purpose strings.
- [ ] Verify logout/deletion clears local cache and secure tokens while preserving only legally required server records.
- [ ] Validate universal links and avoid identifiers in URLs that leak through logs/referrers.

## Approval gate

No production data collection until product, legal/privacy, security, and operations owners sign off this checklist and the resulting retention/deletion schedule.
