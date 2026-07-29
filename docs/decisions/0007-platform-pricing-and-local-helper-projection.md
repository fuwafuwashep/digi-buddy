# ADR 0007: Platform pricing and local helper catalog projection

- Status: Accepted
- Date: 2026-07-20

## Decision

Digibuddy, not an individual helper, owns the customer price schedule. The shared schedule currently presents $29 quick remote help, $49 Standard Help for 30–60 minutes, and a $79 in-home visit. Membership products are displayed at $9.99 for 10 issues, $19.99 for 30 issues, and $99.99 for unlimited monthly help. Membership enrollment, recurring billing, usage entitlements, refunds, and renewal rules remain unimplemented and must be server-authoritative before activation.

Helper onboarding retains its existing `PRICING` step for API and saved-progress compatibility, but the step now records acknowledgment of the Digibuddy pricing policy. It no longer accepts a helper-entered price or public pricing statement. Booking prices are derived by the backend from the platform schedule; client-supplied expected amounts are not authoritative.

Production helper approval remains a staff-controlled server transition. For local desktop testing only, a route enabled exclusively by `digibuddy.environment=local-development` can approve the signed-in, already-submitted application. Approval projects its allowlisted public fields plus privately handled ZIP coverage into the in-memory catalog. The route is absent from non-development compositions, and Android/iOS clients do not expose its control.

## Consequences

- Customer prices are consistent across helpers.
- A helper cannot raise or lower customer charges.
- A developer can exercise the full cross-app flow on one computer without creating a fake production approval mechanism.
- The local projection is ephemeral and disappears when the in-memory backend restarts.
- PostgreSQL production projection, staff review tooling, membership billing, entitlement accounting, and payout economics still require later implementation and integration tests.
