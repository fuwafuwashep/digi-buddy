CREATE TABLE user_identity (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(18) NOT NULL UNIQUE,
    phone_fingerprint CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES user_identity(id) ON DELETE CASCADE,
    role VARCHAR(40) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE phone_verification_attempt (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(18) NOT NULL,
    phone_fingerprint CHAR(64) NOT NULL,
    ip_fingerprint CHAR(64) NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    user_id UUID NULL REFERENCES user_identity(id) ON DELETE CASCADE,
    provider_reference VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    resend_available_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ NULL,
    verified_at TIMESTAMPTZ NULL
);

CREATE INDEX phone_verification_phone_window_idx
    ON phone_verification_attempt (phone_fingerprint, created_at DESC);
CREATE INDEX phone_verification_ip_window_idx
    ON phone_verification_attempt (ip_fingerprint, created_at DESC);

CREATE TABLE email_credential (
    user_id UUID PRIMARY KEY REFERENCES user_identity(id) ON DELETE CASCADE,
    email_normalized VARCHAR(320) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE trusted_device (
    id VARCHAR(160) NOT NULL,
    user_id UUID NOT NULL REFERENCES user_identity(id) ON DELETE CASCADE,
    display_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, user_id)
);

CREATE TABLE refresh_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_identity(id) ON DELETE CASCADE,
    device_id VARCHAR(160) NOT NULL,
    access_token_hash CHAR(64) NOT NULL,
    access_expires_at TIMESTAMPTZ NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    refresh_expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_rotated_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    revocation_reason VARCHAR(80) NULL,
    FOREIGN KEY (device_id, user_id) REFERENCES trusted_device(id, user_id)
);

CREATE INDEX refresh_session_user_idx ON refresh_session (user_id, revoked_at);
CREATE UNIQUE INDEX refresh_session_access_hash_idx ON refresh_session (access_token_hash);
CREATE UNIQUE INDEX refresh_session_refresh_hash_idx ON refresh_session (refresh_token_hash);

CREATE TABLE refresh_token_history (
    session_id UUID NOT NULL REFERENCES refresh_session(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL,
    rotated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, token_hash)
);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    user_id UUID NULL REFERENCES user_identity(id) ON DELETE SET NULL,
    session_id UUID NULL REFERENCES refresh_session(id) ON DELETE SET NULL,
    event_type VARCHAR(80) NOT NULL,
    subject_fingerprint CHAR(64) NULL,
    ip_fingerprint CHAR(64) NULL,
    outcome VARCHAR(40) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX audit_event_user_time_idx ON audit_event (user_id, occurred_at DESC);
