-- ALOUTE V3: cảm xúc, bình luận, chia sẻ

-- Chia sẻ là một bài bình thường trỏ về bài gốc (đã được rút gọn về bài gốc thật sự, không bao giờ trỏ
-- qua một bài chia sẻ khác). NULL nghĩa là bài thường, không phải chia sẻ.
ALTER TABLE posts ADD COLUMN shared_post_id UUID REFERENCES posts (id);
CREATE INDEX ix_posts_shared_post ON posts (shared_post_id) WHERE shared_post_id IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE reactions (
    id         UUID PRIMARY KEY,
    post_id    UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_reactions_type CHECK (type IN ('LOVE', 'HAHA', 'FIRE', 'WOW', 'SAD')),
    -- Mỗi người một cảm xúc cho mỗi bài; đổi loại thì cập nhật hàng này, không tạo thêm hàng
    CONSTRAINT uq_reactions_post_user UNIQUE (post_id, user_id)
);
CREATE INDEX ix_reactions_post ON reactions (post_id);

CREATE TABLE comments (
    id         UUID PRIMARY KEY,
    post_id    UUID          NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES users (id),
    -- Chỉ trả lời MỘT cấp: bình luận gốc có parent_id NULL; CommentService rút gọn trả lời-của-trả lời về đây
    parent_id  UUID          REFERENCES comments (id),
    content    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ            -- xóa mềm: giữ chỗ trong luồng trả lời, hiển thị "đã bị xóa"
);
CREATE INDEX ix_comments_post ON comments (post_id, created_at);
CREATE INDEX ix_comments_parent ON comments (parent_id) WHERE parent_id IS NOT NULL;
