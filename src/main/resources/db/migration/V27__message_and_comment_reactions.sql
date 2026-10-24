-- ALOUTE V27: Thả cảm xúc cho bình luận và tin nhắn + Thông báo cảm xúc bình luận

CREATE TABLE comment_reactions (
    id         UUID PRIMARY KEY,
    comment_id UUID        NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_comment_reactions_type CHECK (type IN ('LOVE', 'HAHA', 'FIRE', 'WOW', 'SAD')),
    CONSTRAINT uq_comment_reactions_comment_user UNIQUE (comment_id, user_id)
);
CREATE INDEX ix_comment_reactions_comment ON comment_reactions (comment_id);

CREATE TABLE message_reactions (
    id         UUID PRIMARY KEY,
    message_id UUID        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_message_reactions_type CHECK (type IN ('LOVE', 'HAHA', 'FIRE', 'WOW', 'SAD')),
    CONSTRAINT uq_message_reactions_message_user UNIQUE (message_id, user_id)
);
CREATE INDEX ix_message_reactions_message ON message_reactions (message_id);

-- Cập nhật ràng buộc loại thông báo cho COMMENT_REACTION
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
    ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
     'POST_SHARED', 'POST_TAGGED', 'DONATION', 'BROADCAST', 'WARNING', 'MENTION', 'SUPPORT', 'REPORT', 'GUESTBOOK_ENTRY', 'COMMENT_REACTION'));
