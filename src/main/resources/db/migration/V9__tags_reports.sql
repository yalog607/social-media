-- ALOUTE V9: gắn thẻ bạn bè trong bài viết và báo cáo vi phạm

CREATE TABLE post_tags (
    id             UUID PRIMARY KEY,
    post_id        UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    tagged_user_id UUID        NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_post_tags_post_user UNIQUE (post_id, tagged_user_id)
);
CREATE INDEX ix_post_tags_user ON post_tags (tagged_user_id);

-- Thông báo "được gắn thẻ"
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
    ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
     'POST_SHARED', 'POST_TAGGED'));

-- Báo cáo: đối tượng bị báo cáo là bài viết, bình luận hoặc người dùng. target_id không có khóa ngoại vì trỏ tới
-- nhiều bảng khác nhau; báo cáo vẫn được giữ lại khi đối tượng bị xóa mềm (Manager còn cần xem lại).
CREATE TABLE reports (
    id          UUID PRIMARY KEY,
    reporter_id UUID         NOT NULL REFERENCES users (id),
    target_type VARCHAR(10)  NOT NULL,
    target_id   UUID         NOT NULL,
    reason      VARCHAR(20)  NOT NULL,
    detail      VARCHAR(500),
    status      VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    handled_by  UUID REFERENCES users (id),
    handled_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_reports_target_type CHECK (target_type IN ('POST', 'COMMENT', 'USER')),
    CONSTRAINT chk_reports_reason CHECK (reason IN ('SPAM', 'HARASSMENT', 'INAPPROPRIATE', 'MISINFO', 'OTHER')),
    CONSTRAINT chk_reports_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);
-- Mỗi người chỉ có một báo cáo ĐANG MỞ cho mỗi đối tượng (chống bấm lặp); báo cáo đã xử lý thì được báo lại.
CREATE UNIQUE INDEX uq_reports_open ON reports (reporter_id, target_type, target_id) WHERE status = 'OPEN';
-- Hàng đợi của Manager: báo cáo đang mở, cũ nhất trước
CREATE INDEX ix_reports_queue ON reports (status, created_at);
