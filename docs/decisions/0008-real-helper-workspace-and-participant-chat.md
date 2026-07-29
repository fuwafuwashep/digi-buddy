# ADR 0008: Real helper workspace and participant chat

## Status

Accepted on 2026-07-20.

## Context

The helper dashboard currently renders fictional request and job cards, while bookings and conversations are readable only by the customer who created them. A public helper catalog identifier is deliberately different from the private authenticated user identifier, so the backend must resolve that relationship without trusting a client-supplied account ID. Approved helpers also need to update the public and service-area information originally collected during onboarding.

## Decision

- Keep one canonical booking model and one canonical chat model.
- Resolve a public helper catalog ID to its authenticated helper user ID on the server when a customer creates a booking or direct conversation.
- Store both customer and helper participants on a conversation. Either authenticated participant may list, read, and send messages, while the welcome conversation remains customer-only and read-only.
- Expose helper-scoped booking reads and commands from the same booking service. The server checks the authenticated helper user ID and remains authoritative for booking status and platform pricing.
- Treat an approved helper's editable profile as updates to the existing helper application steps. Successful updates refresh the public catalog projection without changing approval status.
- Use file bytes selected on the device for profile photos. Validate type, size, and image signature on the backend before updating the public profile URL.
- Do not load fictional helpers or fictional requests in normal runtime repositories. Seed fixtures remain available only when a test opts in, and PostgreSQL seed rows are excluded from public runtime queries.

## Consequences

Customer and helper clients now observe the same requests and messages without introducing another backend, database, identity, booking, or chat system. Helper authorization can be tested independently from public catalog visibility. Native file pickers need platform implementations; Windows can exercise the desktop picker and source compilation can verify Apple/Android integrations, but iOS interaction still requires Xcode on macOS.

