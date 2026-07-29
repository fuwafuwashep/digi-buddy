ALTER TABLE user_identity ADD COLUMN account_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE';

CREATE TABLE customer_profile (
    user_id UUID PRIMARY KEY REFERENCES user_identity(id) ON DELETE CASCADE,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    public_display_name VARCHAR(120) NOT NULL,
    profile_photo_object_key VARCHAR(500) NULL,
    zip_code CHAR(5) NOT NULL,
    location_permission_status VARCHAR(40) NOT NULL DEFAULT 'NOT_REQUESTED',
    notification_permission_status VARCHAR(40) NOT NULL DEFAULT 'NOT_REQUESTED',
    notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    follow_system_text_size BOOLEAN NOT NULL DEFAULT TRUE,
    extra_large_text BOOLEAN NOT NULL DEFAULT FALSE,
    high_contrast BOOLEAN NOT NULL DEFAULT FALSE,
    reduced_motion BOOLEAN NOT NULL DEFAULT FALSE,
    simplified_instructions BOOLEAN NOT NULL DEFAULT TRUE,
    biometric_unlock_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    onboarding_completed_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE customer_saved_address (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES customer_profile(user_id) ON DELETE CASCADE,
    label VARCHAR(80) NOT NULL,
    line1 VARCHAR(160) NOT NULL,
    line2 VARCHAR(160) NULL,
    city VARCHAR(100) NOT NULL,
    region CHAR(2) NOT NULL,
    zip_code CHAR(5) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE customer_technology_preference (
    user_id UUID NOT NULL REFERENCES customer_profile(user_id) ON DELETE CASCADE,
    preference VARCHAR(80) NOT NULL,
    PRIMARY KEY (user_id, preference)
);

CREATE TABLE profile_photo_upload (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_identity(id) ON DELETE CASCADE,
    object_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL
);

CREATE TABLE customer_data_export_request (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_identity(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL
);

CREATE TABLE customer_account_deletion_request (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_identity(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    active_booking_blocker BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMPTZ NULL
);

CREATE INDEX customer_saved_address_user_idx ON customer_saved_address(user_id);
CREATE INDEX customer_export_user_time_idx ON customer_data_export_request(user_id, requested_at DESC);
CREATE INDEX customer_deletion_user_time_idx ON customer_account_deletion_request(user_id, requested_at DESC);
