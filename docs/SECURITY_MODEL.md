# Digibuddy Security Model

**Status:** identity and marketplace development controls implemented; production threat model remains active

**Date:** 2026-07-16

**Implementation status:** customer authentication, booking, chat, payment, and notification foundations exist. Provider certification, persistent production repositories, and release security review remain incomplete.

## Security objectives

1. Protect customer and helper identities, contact information, location/service data, communications, and financial state.
2. Prevent one actor from reading or changing another actor's resources without explicit policy.
3. Keep booking and payment state correct across retries, offline clients, race conditions, provider callbacks, and malicious requests.
4. Minimize data collection, storage, exposure, and retention.
5. Preserve auditability without placing secrets or sensitive content in logs.

## Trust boundaries

- **Mobile apps and devices are untrusted.** Binaries can be inspected, storage can be copied, traffic can be replayed, and UI state can be modified.
- **The public network is untrusted.** All production traffic uses TLS; certificates and endpoints are validated by supported platform stacks.
- **The Ktor backend is the policy and state authority.** It authenticates the caller, authorizes each action, validates transitions, and writes canonical state.
- **The database is sensitive infrastructure.** It is reachable only from approved backend workloads and administrative paths with least privilege.
- **Third-party providers are separate trust domains.** Payment, identity, messaging, email/SMS, maps, and notification integrations require scoped credentials, signed callbacks where available, timeouts, retries, and auditability.
- **Operators and support tools are privileged.** Access requires strong identity, least privilege, separate audit trails, and explicit break-glass procedures.

## Data classification

| Class | Examples | Handling baseline |
| --- | --- | --- |
| Public | published service categories, approved public helper profile fields | Integrity controls; cacheable |
| Internal | feature flags, non-sensitive operational metadata | Authenticated access; limited logs |
| Confidential | names, contact details, precise service locations, booking details, chat metadata/content | Encrypt in transit and at rest; minimize access and retention; redact logs |
| Restricted | refresh tokens, signing keys, provider secrets, password verifiers, financial account tokens | Secrets/secure storage only; never logs or mobile bundles; tightly audited access |
| Prohibited | raw card numbers, card security codes, plaintext passwords | Do not collect or store |

Final retention periods, regional storage requirements, deletion behavior, and legal bases require product/legal decisions before production data is collected.

## Identity and authorization requirements

- Use short-lived, opaque access tokens and rotation/revocation-aware sessions as recorded in ADR 0002.
- Store refresh credentials only in iOS Keychain and Android Keystore-backed secure storage.
- Apply server-side role, ownership, relationship, and resource-state checks on every protected endpoint.
- A `customer`, `helper`, `support`, or `admin` label alone is not sufficient authorization; evaluate the requested action and specific resource.
- Require reauthentication or step-up controls for sensitive account, payout, and recovery actions.
- Rate-limit login, recovery, verification, search, messaging, and state-changing operations using privacy-conscious signals.
- Audit security-sensitive changes, revocations, privilege grants, support access, and high-risk state transitions.

Phone-first identity is implemented with an OTP provider boundary, local random-code adapter, and production Twilio Verify adapter. Optional Argon2id email/password login always requires a phone SMS second factor. SMS is not phishing resistant; helper, support, administration, recovery, and other high-risk roles require a stronger-factor decision before release.

Customer profiles are private authenticated resources. The verified phone is returned only to its account owner; helper/public contracts must use separate allowlisted DTOs and must never expose it. Profile-photo grants validate MIME type and size before issue, uploaded bytes are checked again, and presigned URLs are short-lived bearer capabilities that must not be logged. Account deletion requires a phone-authenticated session no more than ten minutes old, creates an auditable soft-deletion request, and revokes all device sessions. Retention/erasure and active-booking rules remain release blockers.

Helper discovery returns only allowlisted catalog fields and never helper phone numbers, email addresses, user IDs, or precise private addresses. Approval, account status, visibility, active service, and accepting-new-customer rules are applied before filters. Pending, rejected, suspended, and inactive helpers must remain undiscoverable even when a client tampers with query parameters. ZIP centroids and approximate distance are discovery data, not proof of a customer's precise location or travel eligibility.

The Helpers app uses the same identity and rotating-session implementation as the customer app. Its startup and application endpoints are authenticated and owner-scoped, so a customer identity can begin a separate helper application without creating a second account. Legal name, private phone, home ZIP/address, identity documents, bank details, and tax details are excluded from public helper snapshots. H1 does not collect identity documents, bank details, or tax details.

Helper approval is server-authoritative. PostgreSQL approval records an approval event and grants the existing identity's `HELPER` role in one transaction. A local-desktop-only approval route exists solely when the server is explicitly configured as `local-development`; it is absent from production composition and is not exposed by Android or iOS UI. A role alone is insufficient: service activation and paid-request eligibility also require application status `APPROVED`. Under-review, changes-requested, paused, suspended, rejected, and incomplete accounts are denied. Suspension at identity, application, or helper-profile level takes precedence. Future helper booking commands must additionally enforce booking relationship, resource ownership, and allowed server transitions—the client shell is never authorization.

Customer pricing is also server-authoritative. Helpers cannot submit rates, and booking creation ignores client-supplied expected amounts when calculating the price. Membership cards are informational until recurring billing, renewal, cancellation, refund, and usage-entitlement rules are implemented on the backend.

## Secrets management

- Never commit populated `.env` files, credentials, keys, certificates, service-account files, signing stores, or generated secrets.
- Never embed API secrets in either mobile app. Build-time obfuscation is not secret storage.
- Public provider identifiers may be included only when the provider explicitly designs them for untrusted clients.
- Use environment-specific workload identity or a managed secrets service in hosted environments.
- Scope credentials to one service/environment and the minimum permissions; rotate and revoke them with documented procedures.
- Keep development/test data and credentials separate from production.
- CI must scan commits and build artifacts for secrets before release.

## Booking integrity

The booking record and transition rules live on the server. Each command must include authenticated actor context and, where appropriate, an idempotency key and expected record version.

The server must:

- reject illegal transitions and unauthorized actors
- serialize or safely coordinate competing changes
- return the committed canonical state
- keep an audit history of actor, transition, time, and reason without sensitive payload leakage
- emit notifications through a transactional outbox or equivalent reliable pattern
- make cancellation, expiry, dispute, and administrative override rules explicit

Clients may cache and optimistically display a pending request, but must reconcile against the server response and never mark a booking finally accepted, completed, or canceled on their own.

## Payment integrity

The payment foundation is implemented with a local adapter, a server-side Stripe boundary, signed-webhook verification, idempotent intent creation, and immutable ledger/receipt records. Before production release:

- Select a provider that supports the intended marketplace, payout, refund, dispute, identity/KYC, and regional model.
- Use provider SDKs or hosted elements to tokenize payment details; raw card data must not pass through Digibuddy systems.
- Create payment intents and authoritative amount/currency/order relationships on the server.
- Use unique idempotency keys for charge, refund, payout, and webhook processing.
- Verify webhook signatures, timestamps, expected account/environment, and event uniqueness before applying state.
- Record an immutable ledger/reconciliation history rather than mutating away financial facts.
- Never infer successful payment from a client callback or redirect alone.
- Separate booking status from payment status while defining explicit allowed interactions between them.

## Chat and notification safety

Chat now enforces conversation membership on reads/writes, uses server sequence identifiers and client idempotency IDs, and supports blocking/reporting. Retention, moderation operations, deletion/legal hold, attachment malware scanning, and abuse escalation remain production blockers.

Push notifications must contain opaque identifiers and minimal display text. Do not put access tokens, precise addresses, payment facts, or private message contents in notification payloads or routine telemetry. Devices must resynchronize from the authenticated backend.

## Mobile and local data

- SQLDelight is a convenience/offline cache, not a trusted source of permission or final state.
- Cache the minimum data needed and delete it on logout/account removal according to policy.
- Keep tokens and high-value credentials out of SQLDelight and plain preferences.
- Avoid logging HTTP bodies by default; development logging must redact authorization, cookies, personal data, and chat/payment content.
- Validate deep links and universal/app links; do not perform sensitive actions solely from link parameters.
- Treat rooted/jailbroken device detection, certificate pinning, and attestation as defense-in-depth decisions, not substitutes for server authorization.

## Backend and infrastructure baseline

- Validate request size, content type, schema, ranges, and identifiers at the API boundary.
- Use parameterized database access and least-privilege database roles.
- Apply timeouts, bounded retries with jitter, circuit breaking where useful, and concurrency limits.
- Do not expose internal exception details, SQL, stack traces, or secret configuration to clients.
- Use structured, redacted logs with request/correlation IDs and separate security audit storage.
- Encrypt managed data stores and backups; test restoration and deletion paths.
- Restrict administrative interfaces by strong identity and network policy, and audit every privileged action.
- Keep dependencies locked and verified; generate an SBOM and scan dependencies/containers in CI.
- Run migrations through reviewed, repeatable tooling with backup and rollback/forward-fix plans.

## Required security verification by phase

- **Scaffolding:** dependency verification, secret scanning, no populated environment files, safe sample configuration.
- **Foundations:** API boundary tests, serialization fuzz/invalid-input cases, redaction tests, database migration tests.
- **Identity:** authorization matrix, token lifecycle, replay, enumeration, recovery, rate-limit, and revocation tests.
- **Bookings:** transition property tests, concurrency, idempotency, ownership, and audit tests.
- **Chat:** membership isolation, pagination/sequence, abuse, notification privacy, and retention tests.
- **Payments:** provider sandbox, signature, replay, idempotency, reconciliation, refund/dispute, and amount/currency tamper tests.
- **Release:** threat-model review, static/dependency/container/secret scans, penetration testing, backup/restore exercise, and incident runbook review.

## Open security decisions

- Account recovery policy and phishing-resistant factors for privileged roles
- Marketplace payment/funds-flow model and compliance scope
- Data residency, privacy jurisdiction, retention, deletion, and support-access rules
- Hosting provider, network topology, managed secrets system, and observability vendor
- Abuse prevention, user reporting, moderation, and emergency escalation
- Mobile deployment minimums and whether any defense-in-depth attestation/pinning is justified

These decisions must be documented before their associated feature phase. They must not be silently replaced later.
