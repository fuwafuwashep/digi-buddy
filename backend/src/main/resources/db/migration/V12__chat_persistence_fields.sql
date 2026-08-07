ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS customer_user_id UUID REFERENCES user_identity(id);

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS helper_user_id UUID REFERENCES user_identity(id);

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS customer_display_name VARCHAR(120);

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS helper_display_name VARCHAR(120);

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS can_reply BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS blocked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS sender_display_name VARCHAR(120);

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS attachment_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS delivery_status VARCHAR(30) NOT NULL DEFAULT 'DELIVERED';

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS development_seed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE conversations AS conversation
SET
    customer_user_id = booking.customer_user_id,
    helper_user_id = booking.helper_user_id
FROM bookings AS booking
WHERE conversation.booking_id = booking.id
  AND conversation.customer_user_id IS NULL;

UPDATE conversations AS conversation
SET customer_display_name = profile.public_display_name
FROM customer_profile AS profile
WHERE conversation.customer_user_id = profile.user_id
  AND conversation.customer_display_name IS NULL;

UPDATE conversations AS conversation
SET helper_display_name = profile.display_name
FROM helper_profile AS profile
WHERE conversation.helper_user_id = profile.user_id
  AND conversation.helper_display_name IS NULL;

UPDATE conversations
SET customer_display_name = 'Customer'
WHERE customer_user_id IS NOT NULL
  AND customer_display_name IS NULL;

UPDATE conversations
SET helper_display_name = 'Helper'
WHERE helper_user_id IS NOT NULL
  AND helper_display_name IS NULL;

UPDATE chat_messages AS message
SET sender_display_name =
    CASE
        WHEN message.sender_user_id = conversation.customer_user_id
            THEN COALESCE(conversation.customer_display_name, 'Customer')
        WHEN message.sender_user_id = conversation.helper_user_id
            THEN COALESCE(conversation.helper_display_name, 'Helper')
        ELSE 'Digibuddy'
    END
FROM conversations AS conversation
WHERE message.conversation_id = conversation.id
  AND message.sender_display_name IS NULL;

CREATE INDEX IF NOT EXISTS conversations_customer_user_idx
    ON conversations(customer_user_id);

CREATE INDEX IF NOT EXISTS conversations_helper_user_idx
    ON conversations(helper_user_id);

CREATE INDEX IF NOT EXISTS chat_messages_sender_client_idx
    ON chat_messages(sender_user_id, client_message_id);
