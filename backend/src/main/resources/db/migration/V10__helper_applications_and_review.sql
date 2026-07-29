CREATE TABLE helper_application (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES user_identity(id) ON DELETE RESTRICT,
    status VARCHAR(48) NOT NULL,
    current_step VARCHAR(48) NOT NULL,
    submitted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    CHECK (status IN (
        'PROFILE_INCOMPLETE', 'IDENTITY_INFORMATION_REQUIRED', 'PAYMENT_ONBOARDING_REQUIRED',
        'UNDER_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'PAUSED_BY_HELPER',
        'SUSPENDED', 'REJECTED'
    )),
    CHECK (version > 0)
);

CREATE TABLE helper_application_step (
    application_id UUID NOT NULL REFERENCES helper_application(id) ON DELETE CASCADE,
    step VARCHAR(48) NOT NULL,
    payload_json JSONB NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    saved_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (application_id, step)
);

CREATE TABLE helper_application_requirement (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES helper_application(id) ON DELETE CASCADE,
    requirement_code VARCHAR(80) NOT NULL,
    label VARCHAR(160) NOT NULL,
    visibility VARCHAR(16) NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    required BOOLEAN NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('NOT_STARTED', 'COMPLETE', 'NEEDS_ATTENTION')),
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (application_id, requirement_code)
);

CREATE TABLE helper_profile_status (
    application_id UUID PRIMARY KEY REFERENCES helper_application(id) ON DELETE CASCADE,
    user_id UUID NOT NULL UNIQUE REFERENCES user_identity(id) ON DELETE RESTRICT,
    status VARCHAR(48) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE helper_approval_event (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES helper_application(id) ON DELETE CASCADE,
    from_status VARCHAR(48) NOT NULL,
    to_status VARCHAR(48) NOT NULL,
    actor_user_id UUID NULL REFERENCES user_identity(id) ON DELETE SET NULL,
    reason VARCHAR(1000) NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE helper_required_change (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES helper_application(id) ON DELETE CASCADE,
    step VARCHAR(48) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ NULL
);

CREATE TABLE helper_staff_review (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES helper_application(id) ON DELETE CASCADE,
    assigned_staff_user_id UUID NULL REFERENCES user_identity(id) ON DELETE SET NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'UNASSIGNED',
    private_notes VARCHAR(2000) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (review_status IN ('UNASSIGNED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX helper_application_status_idx ON helper_application(status, updated_at);
CREATE INDEX helper_required_change_open_idx ON helper_required_change(application_id, resolved_at);
CREATE INDEX helper_approval_event_application_idx ON helper_approval_event(application_id, occurred_at);

-- Compatibility backfill for any real, identity-linked helper profile created before H1.
-- Fictional catalog seeds have no user_id and are intentionally excluded.
INSERT INTO helper_application (
    id, user_id, status, current_step, submitted_at, created_at, updated_at, version
)
SELECT
    md5('helper-application:' || profile.user_id::text)::uuid,
    profile.user_id,
    CASE
        WHEN profile.account_status = 'SUSPENDED' OR profile.approval_status = 'SUSPENDED' THEN 'SUSPENDED'
        WHEN profile.approval_status = 'APPROVED' THEN 'APPROVED'
        WHEN profile.approval_status = 'PENDING' THEN 'UNDER_REVIEW'
        ELSE 'REJECTED'
    END,
    'PAYOUT_ONBOARDING',
    profile.updated_at,
    profile.created_at,
    profile.updated_at,
    1
FROM helper_profile profile
WHERE profile.user_id IS NOT NULL
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO helper_profile_status (application_id, user_id, status, updated_at)
SELECT application.id, application.user_id, application.status, application.updated_at
FROM helper_application application
ON CONFLICT (application_id) DO NOTHING;

INSERT INTO user_role (user_id, role, granted_at)
SELECT application.user_id, 'HELPER', application.updated_at
FROM helper_application application
WHERE application.status IN ('APPROVED', 'PAUSED_BY_HELPER', 'SUSPENDED')
ON CONFLICT (user_id, role) DO NOTHING;
