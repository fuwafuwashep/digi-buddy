CREATE TABLE zip_code_location (
    zip_code CHAR(5) PRIMARY KEY,
    city VARCHAR(100) NOT NULL,
    region CHAR(2) NOT NULL,
    centroid geography(Point, 4326) NOT NULL
);
CREATE INDEX zip_code_location_centroid_gix ON zip_code_location USING GIST (centroid);

CREATE TABLE service_category (
    id UUID PRIMARY KEY,
    slug VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE skill (
    id UUID PRIMARY KEY,
    slug VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE language (
    code VARCHAR(12) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE helper_profile (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NULL REFERENCES user_identity(id) ON DELETE RESTRICT,
    display_name VARCHAR(100) NOT NULL,
    headline VARCHAR(160) NOT NULL,
    biography VARCHAR(1200) NOT NULL,
    profile_photo_url VARCHAR(500) NULL,
    account_status VARCHAR(40) NOT NULL,
    approval_status VARCHAR(40) NOT NULL,
    verification_status VARCHAR(40) NOT NULL,
    catalog_visible BOOLEAN NOT NULL DEFAULT FALSE,
    seed_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (approval_status IN ('PENDING', 'APPROVED', 'SUSPENDED', 'REJECTED')),
    CHECK (verification_status IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'EXPIRED'))
);

CREATE TABLE helper_skill (
    helper_id UUID NOT NULL REFERENCES helper_profile(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skill(id) ON DELETE RESTRICT,
    years_experience INTEGER NOT NULL CHECK (years_experience BETWEEN 0 AND 80),
    PRIMARY KEY (helper_id, skill_id)
);

CREATE TABLE helper_service (
    id UUID PRIMARY KEY,
    helper_id UUID NOT NULL REFERENCES helper_profile(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES service_category(id) ON DELETE RESTRICT,
    starting_price_cents INTEGER NOT NULL CHECK (starting_price_cents >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    remote_service BOOLEAN NOT NULL,
    in_person_service BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (remote_service OR in_person_service),
    UNIQUE (helper_id, category_id)
);

CREATE TABLE helper_service_area (
    id UUID PRIMARY KEY,
    helper_id UUID NOT NULL REFERENCES helper_profile(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    origin geography(Point, 4326) NOT NULL,
    service_radius_miles NUMERIC(6,2) NULL CHECK (service_radius_miles > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX helper_service_area_origin_gix ON helper_service_area USING GIST (origin);

CREATE TABLE helper_service_zip_code (
    service_area_id UUID NOT NULL REFERENCES helper_service_area(id) ON DELETE CASCADE,
    zip_code CHAR(5) NOT NULL REFERENCES zip_code_location(zip_code) ON DELETE RESTRICT,
    PRIMARY KEY (service_area_id, zip_code)
);

CREATE TABLE helper_language (
    helper_id UUID NOT NULL REFERENCES helper_profile(id) ON DELETE CASCADE,
    language_code VARCHAR(12) NOT NULL REFERENCES language(code) ON DELETE RESTRICT,
    PRIMARY KEY (helper_id, language_code)
);

CREATE TABLE helper_availability_summary (
    helper_id UUID PRIMARY KEY REFERENCES helper_profile(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    next_available_at TIMESTAMPTZ NULL,
    available_within_days INTEGER NULL CHECK (available_within_days >= 0),
    accepting_new_customers BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE helper_performance_summary (
    helper_id UUID PRIMARY KEY REFERENCES helper_profile(id) ON DELETE CASCADE,
    average_rating NUMERIC(3,2) NOT NULL CHECK (average_rating BETWEEN 0 AND 5),
    review_count INTEGER NOT NULL CHECK (review_count >= 0),
    completed_job_count INTEGER NOT NULL CHECK (completed_job_count >= 0),
    median_response_time_minutes INTEGER NOT NULL CHECK (median_response_time_minutes >= 0),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX helper_profile_catalog_eligibility_idx
    ON helper_profile (approval_status, account_status, catalog_visible, verification_status);
CREATE INDEX helper_service_filter_idx
    ON helper_service (category_id, active, remote_service, in_person_service, starting_price_cents);
CREATE INDEX helper_availability_filter_idx
    ON helper_availability_summary (accepting_new_customers, available_within_days, next_available_at);
CREATE INDEX helper_performance_sort_idx
    ON helper_performance_summary (average_rating DESC, completed_job_count DESC);
