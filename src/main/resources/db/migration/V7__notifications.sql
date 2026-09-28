-- ALOUTE V7: thông báo trong ứng dụng

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    recipient_id UUID        NOT NULL REFERENCES users (id),
    actor_id     UUID        NOT NULL REFERENCES users (id),
    type         VARCHAR(20) NOT NULL,
    post_id      UUID REFERENCES posts (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ,
    CONSTRAINT chk_notifications_type CHECK (type IN
        ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY', 'POST_SHARED'))
);
-- Trang thông báo luôn lọc theo người nhận rồi sắp xếp mới nhất trước; đếm chưa đọc lọc thêm read_at IS NULL.
CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, created_at DESC);
