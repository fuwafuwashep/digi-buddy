# Digibuddy Operations Runbook

**Status:** pre-production outline

## Service health

Monitor backend availability, request latency/error rates, database pool saturation, Redis health, Flyway version, WebSocket connections/reconnects, OTP provider outcomes, rate-limit/lockout rates, booking transition failures, chat delivery lag, payment reconciliation differences, webhook signature failures, push invalid-token rates, and storage upload failures.

`GET /health` confirms process health only. Add authenticated dependency readiness checks before hosted rollout; do not expose database or secret details in public responses.

## Incident priorities

- **P0:** unauthorized data access, secret/key compromise, incorrect payment capture/refund, broad outage, or destructive data loss
- **P1:** authentication unavailable, booking state corruption, chat isolation failure, provider webhook backlog, or sustained regional outage
- **P2:** degraded search, delayed notifications, individual upload failures, or non-critical UI regression

For P0/P1: assign an incident lead, freeze risky releases, preserve evidence and audit records, revoke/rotate affected credentials, disable unsafe operations with a server-side control, communicate through the approved channel, and start the legal/privacy notification assessment. Never delete logs or financial facts during response.

## Provider failure behavior

- OTP: return generic recoverable errors; retain rate limits; never reveal account existence.
- Stripe: do not infer success from the client. Queue/replay signed unique webhooks and reconcile provider state to the immutable ledger.
- APNs: remove invalid tokens, retry transient failures with bounds/jitter, and let clients resynchronize from the backend.
- Object storage: expire failed grants, validate bytes after upload, quarantine unscanned objects, and allow safe retry.
- WebSockets: fall back to authenticated HTTP synchronization using server sequence values.

## Data operations

All privileged queries and corrections require a ticket, named operator, least-privilege role, reason, before/after evidence, and audit event. Booking/payment corrections must use explicit compensating records; never rewrite or delete ledger history.

Test database restore, point-in-time recovery, audit export, provider reconciliation, token-key rotation, APNs/Stripe key rotation, and regional failover before launch and on a documented schedule.

## Release verification

Run the Gradle build, tests, ktlint, Detekt, Android lint/APK assembly, macOS iOS framework/app build, Docker Compose validation, migrations against an isolated PostGIS database, secret/dependency/container scans, and provider sandbox smoke tests. Record exact command output in `docs/PROJECT_STATUS.md`.
