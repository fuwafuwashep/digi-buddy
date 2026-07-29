# ADR 0002: Phone authentication and session security

**Status:** Accepted

**Date:** 2026-07-16

## Context

The customer and future helper applications require one identity system. The phone number is the primary account identifier. The system must support passwordless phone signup/login, optional email/password credentials with SMS as a mandatory second factor, server-side abuse controls, and revocable device sessions without placing provider secrets or refresh tokens in mobile application storage that is not designed for secrets.

The prior plan deferred identity until this decision was authorized. The phone-authentication request authorizes Phase 3 work even though macOS and Docker verification gaps from Phase 1 remain documented.

## Decision

- The backend normalizes and validates phone numbers with libphonenumber and stores only canonical E.164 values.
- OTP delivery is behind an `OtpProvider` port. Production supports Twilio Verify using server-only credentials. Local development uses a random, per-attempt development code and refuses to start in production mode.
- OTP attempts expire after five minutes, may not be resent for sixty seconds, allow five checks, and apply per-phone/per-IP windows plus a fifteen-minute temporary lockout.
- Access and refresh credentials are opaque random bearer tokens. Only keyed or one-way hashes are stored server-side. Access tokens expire after ten minutes.
- Refresh tokens rotate on every use. Previously used token hashes are retained for the session family; reuse revokes that family and creates an audit event.
- Sessions are associated with device records. Current-device and all-device revocation are backend operations.
- iOS stores the refresh token in Keychain. Android encrypts it with an AES/GCM key held by Android Keystore; preferences contain ciphertext only.
- Email/password credentials can be added only from an authenticated phone session. Passwords use Argon2id with OWASP's minimum baseline of 19 MiB memory, two iterations, and one lane. An email/password match creates an SMS challenge and never completes login by itself.
- Authentication logs contain event types, opaque IDs, and keyed phone/IP fingerprints, never phone numbers, OTPs, passwords, access tokens, or refresh tokens.

## Consequences

- Opaque access credentials require a session lookup, which provides immediate revocation at the cost of a database/cache read. Redis may later cache short-lived access lookups without becoming authoritative.
- SMS possession is not phishing-resistant. Higher-risk helper, payout, support, and administrative roles will require stronger factors before those roles are implemented.
- Twilio availability affects new challenges but does not expose Twilio credentials to either app.
- Production startup must fail when Twilio, database, identifier-HMAC, or TLS/proxy configuration is missing or unsafe.
- Email ownership verification and account recovery policy remain separate decisions; adding an email/password credential does not replace the verified phone identity.
