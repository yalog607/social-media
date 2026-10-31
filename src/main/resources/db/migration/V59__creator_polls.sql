-- ALOUTE V54: Bình chọn (poll) của Creator. Poll là một bài viết thật (posts.post_kind = 'POLL') kèm các lựa chọn và phiếu bầu.

-- 1) Loại bài: bài thường mặc định; POLL và QUESTION do Creator tạo
ALTER TABLE posts ADD COLUMN post_kind VARCHAR(16) NOT NULL DEFAULT 'POST';
ALTER TABLE posts ADD CONSTRAINT chk_posts_post_kind CHECK (post_kind IN ('POST', 'POLL', 'QUESTION'));
ALTER TABLE posts ADD COLUMN poll_closes_at TIMESTAMPTZ;

-- 2) Các lựa chọn của poll
CREATE TABLE poll_options (
    id       UUID PRIMARY KEY,
    post_id  UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    position SMALLINT    NOT NULL,
    label    VARCHAR(80) NOT NULL,
    CONSTRAINT uq_poll_options_position UNIQUE (post_id, position)
);

-- 3) Phiếu bầu: mỗi người một phiếu cho mỗi poll (chọn một, không đổi)
CREATE TABLE poll_votes (
    post_id    UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    option_id  UUID        NOT NULL REFERENCES poll_options (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX ix_poll_votes_option ON poll_votes (option_id);

-- 4) Thông báo poll mới cho người theo dõi
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
    ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
     'POST_SHARED', 'POST_TAGGED', 'BROADCAST', 'WARNING', 'MENTION', 'SUPPORT', 'REPORT',
     'GUESTBOOK_ENTRY', 'COMMENT_REACTION', 'STREAK', 'STICKER_REWARD', 'SEASON_JOIN', 'SEASON_RESULT', 'SEASON_BANNED',
     'CREATOR_POLL'));
