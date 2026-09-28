-- ALOUTE V8: chat 1-1 và nhóm

-- direct_key chỉ có ở hội thoại DIRECT, chuẩn hóa "idNhỏ_idLớn" (so sánh chuỗi hex, xem ChatService#directKey)
-- để không bao giờ tạo hai hội thoại 1-1 khác nhau cho cùng một cặp người dùng.
CREATE TABLE conversations (
    id          UUID PRIMARY KEY,
    type        VARCHAR(10)  NOT NULL,
    title       VARCHAR(100),
    direct_key  VARCHAR(80),
    created_by  UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_conversations_type CHECK (type IN ('DIRECT', 'GROUP')),
    CONSTRAINT uq_conversations_direct_key UNIQUE (direct_key)
);

CREATE TABLE conversation_members (
    id              UUID PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id),
    user_id         UUID        NOT NULL REFERENCES users (id),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_at    TIMESTAMPTZ,
    CONSTRAINT uq_conversation_members UNIQUE (conversation_id, user_id)
);
CREATE INDEX ix_conversation_members_user ON conversation_members (user_id);

CREATE TABLE messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id),
    sender_id       UUID        NOT NULL REFERENCES users (id),
    content         VARCHAR(2000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_messages_conversation ON messages (conversation_id, created_at);

-- Mỗi tin nhắn nhiều nhất một file đính kèm (ảnh/video/file thường); IMAGE/VIDEO hiển thị trực tiếp,
-- FILE hiển thị như một đường tải xuống kèm original_name (tên file gốc, không dùng để đặt tên lúc lưu).
CREATE TABLE message_attachments (
    id            UUID PRIMARY KEY,
    message_id    UUID          NOT NULL UNIQUE REFERENCES messages (id),
    kind          VARCHAR(10)   NOT NULL,
    url           VARCHAR(500)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    original_name VARCHAR(255)  NOT NULL,
    CONSTRAINT chk_message_attachments_kind CHECK (kind IN ('IMAGE', 'VIDEO', 'FILE'))
);
