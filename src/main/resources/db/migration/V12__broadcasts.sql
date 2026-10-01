-- ALOUTE V12: Creator nhắn hàng loạt tới người theo dõi hoặc fan; người nhận thấy trong trang thông báo.
CREATE TABLE broadcasts (
    id              UUID PRIMARY KEY,
    creator_id      UUID         NOT NULL REFERENCES users (id),
    audience        VARCHAR(10)  NOT NULL,
    title           VARCHAR(80)  NOT NULL,
    body            VARCHAR(500) NOT NULL,
    recipient_count INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_broadcasts_audience CHECK (audience IN ('FOLLOWERS', 'FANS'))
);
CREATE INDEX ix_broadcasts_creator ON broadcasts (creator_id, created_at DESC);

ALTER TABLE notifications ADD COLUMN broadcast_id UUID REFERENCES broadcasts (id);
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
    ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
     'POST_SHARED', 'POST_TAGGED', 'DONATION', 'BROADCAST'));
