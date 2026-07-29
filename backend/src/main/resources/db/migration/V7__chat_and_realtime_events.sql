CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    booking_id UUID REFERENCES bookings(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_sequence_id BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE conversation_participants (
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    user_id UUID NOT NULL REFERENCES user_identities(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_read_sequence_id BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    sender_user_id UUID REFERENCES user_identities(id),
    client_message_id VARCHAR(128) NOT NULL,
    sequence_id BIGINT NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (conversation_id, client_message_id),
    UNIQUE (conversation_id, sequence_id)
);

CREATE TABLE message_attachments (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_messages(id),
    object_key TEXT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760)
);

CREATE TABLE message_receipts (
    message_id UUID NOT NULL REFERENCES chat_messages(id),
    user_id UUID NOT NULL REFERENCES user_identities(id),
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    PRIMARY KEY (message_id, user_id)
);

CREATE TABLE user_blocks (
    blocker_user_id UUID NOT NULL REFERENCES user_identities(id),
    blocked_user_id UUID NOT NULL REFERENCES user_identities(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_user_id, blocked_user_id),
    CHECK (blocker_user_id <> blocked_user_id)
);

CREATE TABLE message_reports (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_messages(id),
    reporter_user_id UUID NOT NULL REFERENCES user_identities(id),
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX messages_conversation_sequence_idx ON chat_messages(conversation_id, sequence_id DESC);
CREATE INDEX conversations_booking_idx ON conversations(booking_id);
