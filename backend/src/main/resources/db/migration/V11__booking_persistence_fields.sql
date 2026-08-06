ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS helper_user_id UUID REFERENCES user_identity(id);

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS customer_display_name VARCHAR(120);

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS request_json JSONB;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS price_json JSONB;

UPDATE bookings AS booking
SET helper_user_id = profile.user_id
FROM helper_profile AS profile
WHERE booking.helper_profile_id = profile.id
  AND booking.helper_user_id IS NULL
  AND profile.user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS bookings_helper_user_status_idx
    ON bookings(helper_user_id, status, scheduled_start);
