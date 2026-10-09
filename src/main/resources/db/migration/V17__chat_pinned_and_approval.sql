ALTER TABLE messages
ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE conversations
ADD COLUMN approval_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE conversation_join_requests (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (conversation_id, user_id)
);
