CREATE TABLE push_device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_identity(id),
    device_id VARCHAR(200) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    app_environment VARCHAR(30) NOT NULL,
    token_ciphertext TEXT NOT NULL,
    token_fingerprint CHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, device_id),
    UNIQUE (token_fingerprint)
);

CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES user_identity(id),
    security_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    bookings_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    messages_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    payments_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_identity(id),
    device_token_id UUID REFERENCES push_device_tokens(id),
    event_type VARCHAR(80) NOT NULL,
    provider_message_id TEXT,
    status VARCHAR(30) NOT NULL,
    deep_link JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMPTZ
);

CREATE INDEX notification_delivery_user_created_idx
    ON notification_deliveries(user_id, created_at DESC);