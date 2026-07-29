# Digibuddy Deployment Guide

**Status:** development architecture; no production deployment is authorized yet

## Environments

Use separate local, test, staging, and production environments. Each environment needs isolated databases, Redis, object storage, provider accounts, signing material, domains, audit storage, and encryption keys. Never copy production customer data into development.

The Ktor backend is the only trusted entry point for the customer and Helpers clients. Run it behind TLS termination and a managed load balancer. PostgreSQL/PostGIS is the system of record; Redis is for bounded ephemeral coordination, not canonical booking or payment state.

## Required hosted services

- PostgreSQL with PostGIS, encrypted backups, point-in-time recovery, and a least-privilege application role
- Redis with authentication, encryption, eviction monitoring, and no public network exposure
- Server-side secrets manager for token keys, database credentials, Twilio, Stripe, APNs, and object-storage credentials
- S3-compatible private object storage with short-lived presigned uploads, content scanning, and lifecycle policies
- Central structured logs, metrics, traces, alerting, and append-only security audit retention
- Twilio Verify, Stripe marketplace-capable account, and APNs credentials configured per environment

## Release sequence

1. Build and scan immutable backend/container and mobile artifacts from a tagged revision.
2. Back up the database and review forward-only Flyway migrations.
3. Apply migrations once using a dedicated migration identity.
4. Deploy backend instances with readiness and liveness checks; verify `/health` internally.
5. Run authentication, catalog, booking, chat, payment-webhook, notification, and authorization smoke tests.
6. Release mobile builds gradually and monitor error, latency, OTP, booking, chat, and payment reconciliation signals.

## Configuration gates

Hosted environments must reject in-memory authentication and development OTP. The same gate must be extended to reject process-memory booking/chat/payment/notification repositories before launch. Real provider transports must fail closed when credentials or signing verification are absent.

Required production values include unique token/identifier secrets, database credentials, Twilio Verify values, Stripe server/webhook secrets, APNs team/key/topic values, storage endpoint/bucket credentials, public API URL, and approved universal-link domains. Do not place these values in Gradle files, Xcode configuration committed to Git, mobile resources, or Docker images.

## Rollback

Prefer backward-compatible deploys and forward fixes. Do not automatically roll back a schema after it has accepted production writes. If application rollback is required, keep the migrated schema compatible, stop unsafe consumers, preserve audit/payment events, and reconcile all provider webhooks before resuming traffic.

## Current blockers

Production deployment is blocked by persistent repositories for V6-V9 services, provider transport implementation/certification, hosted secret management, abuse/moderation operations, data-retention decisions, disaster-recovery testing, macOS release builds, and external security/privacy review.
