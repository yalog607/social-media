-- ALOUTE V13: kiểm duyệt (chế tài), nhật ký hệ thống, hỗ trợ người dùng, hashtag bị cấm, cấu hình hệ thống

-- Chế tài: cảnh cáo hoặc khóa tài khoản. lifted_at khác NULL nghĩa là đã gỡ (chỉ có ý nghĩa với khóa).
CREATE TABLE sanctions (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id),
    type       VARCHAR(10)  NOT NULL,
    reason     VARCHAR(300) NOT NULL,
    report_id  UUID REFERENCES reports (id),
    imposed_by UUID         NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    lifted_at  TIMESTAMPTZ,
    lifted_by  UUID REFERENCES users (id),
    CONSTRAINT chk_sanctions_type CHECK (type IN ('WARNING', 'SUSPENSION'))
);
CREATE INDEX ix_sanctions_user ON sanctions (user_id, created_at DESC);

-- Nhật ký hệ thống: mọi hành động nhạy cảm của Manager/Admin. Chỉ thêm, không sửa/xóa.
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY,
    actor_id    UUID         REFERENCES users (id),
    action      VARCHAR(30)  NOT NULL,
    target_type VARCHAR(20),
    target_id   UUID,
    detail      VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_time ON audit_logs (created_at DESC, id DESC);

-- Hỗ trợ người dùng: một phiếu có một lần phản hồi của Manager
CREATE TABLE support_tickets (
    id         UUID PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id),
    subject    VARCHAR(100)  NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    status     VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    reply      VARCHAR(1000),
    handled_by UUID REFERENCES users (id),
    handled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_tickets_status CHECK (status IN ('OPEN', 'CLOSED'))
);
CREATE INDEX ix_tickets_queue ON support_tickets (status, created_at);
CREATE INDEX ix_tickets_user ON support_tickets (user_id, created_at DESC);

-- Hashtag bị cấm: bài mới chứa các thẻ này bị từ chối
CREATE TABLE banned_hashtags (
    tag        VARCHAR(50) PRIMARY KEY,
    banned_by  UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Cấu hình hệ thống do Admin chỉnh
CREATE TABLE system_settings (
    key        VARCHAR(50) PRIMARY KEY,
    value      VARCHAR(200) NOT NULL,
    updated_by UUID REFERENCES users (id),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
INSERT INTO system_settings (key, value) VALUES ('registration_open', 'true');

ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
    ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
     'POST_SHARED', 'POST_TAGGED', 'DONATION', 'BROADCAST', 'WARNING'));
