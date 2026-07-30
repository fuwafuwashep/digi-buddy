CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    customer_user_id UUID NOT NULL REFERENCES user_identity(id),
    helper_profile_id UUID NOT NULL REFERENCES helper_profile(id),
    service_category_id UUID NOT NULL REFERENCES service_category(id),
    service_mode VARCHAR(20) NOT NULL CHECK (service_mode IN ('REMOTE', 'IN_PERSON')),
    pricing_type VARCHAR(30) NOT NULL CHECK (pricing_type IN ('FIXED', 'HOURLY', 'QUOTE_REQUIRED')),
    problem_description TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (customer_user_id, idempotency_key),
    CHECK (scheduled_end > scheduled_start)
);

CREATE TABLE booking_status_history (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    actor_user_id UUID REFERENCES user_identity(id),
    actor_type VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    audit_detail JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE booking_service_addresses (
    booking_id UUID PRIMARY KEY REFERENCES bookings(id),
    encrypted_line1 TEXT NOT NULL,
    encrypted_line2 TEXT,
    city VARCHAR(120) NOT NULL,
    region VARCHAR(80) NOT NULL,
    zip_code VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE booking_attachments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    object_key TEXT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE booking_quotes (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    revision INTEGER NOT NULL,
    labor_cents INTEGER NOT NULL CHECK (labor_cents >= 0),
    materials_cents INTEGER NOT NULL DEFAULT 0 CHECK (materials_cents >= 0),
    travel_cents INTEGER NOT NULL DEFAULT 0 CHECK (travel_cents >= 0),
    platform_fee_cents INTEGER NOT NULL DEFAULT 0 CHECK (platform_fee_cents >= 0),
    total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (booking_id, revision)
);

CREATE TABLE booking_change_orders (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    description TEXT NOT NULL,
    amount_delta_cents INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE TABLE booking_cancellations (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    canceled_by_user_id UUID REFERENCES user_identity(id),
    reason TEXT NOT NULL,
    fee_cents INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX bookings_customer_status_idx ON bookings(customer_user_id, status, scheduled_start);
CREATE INDEX bookings_helper_schedule_idx ON bookings(helper_profile_id, scheduled_start, scheduled_end);
CREATE INDEX booking_history_booking_idx ON booking_status_history(booking_id, occurred_at);
