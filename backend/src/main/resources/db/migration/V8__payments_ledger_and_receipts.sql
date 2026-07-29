CREATE TABLE payments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    customer_user_id UUID NOT NULL REFERENCES user_identities(id),
    provider VARCHAR(30) NOT NULL,
    provider_payment_id TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    amount_cents INTEGER NOT NULL CHECK (amount_cents >= 0),
    currency CHAR(3) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (customer_user_id, idempotency_key),
    UNIQUE (provider, provider_payment_id)
);

CREATE TABLE payment_ledger_entries (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    entry_type VARCHAR(50) NOT NULL,
    amount_cents INTEGER NOT NULL,
    currency CHAR(3) NOT NULL,
    provider_event_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payment_webhook_events (
    provider VARCHAR(30) NOT NULL,
    provider_event_id TEXT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    PRIMARY KEY (provider, provider_event_id)
);

CREATE TABLE payment_receipts (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    payment_id UUID NOT NULL REFERENCES payments(id),
    receipt_data JSONB NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (booking_id, payment_id)
);

CREATE INDEX payments_booking_idx ON payments(booking_id);
CREATE INDEX ledger_payment_created_idx ON payment_ledger_entries(payment_id, created_at);
