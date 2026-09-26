-- ALOUTE V2 (Giai đoạn 2a): bài đăng, media, hashtag

-- Chỉ mục trigram giúp tìm kiếm ILIKE '%...%' nhanh (dùng ở phần 2c). Là extension "trusted" nên chủ database tạo được.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE posts (
    id          UUID PRIMARY KEY,
    author_id   UUID          NOT NULL REFERENCES users (id),
    content     VARCHAR(2000) NOT NULL DEFAULT '',
    visibility  VARCHAR(20)   NOT NULL DEFAULT 'PUBLIC',
    -- Nội dung đã bỏ dấu, chữ thường: dùng để tìm kiếm không phân biệt dấu
    search_text TEXT          NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    edited_at   TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,            -- xóa mềm
    CONSTRAINT chk_posts_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'))
);

-- Bảng tin và trang cá nhân: phân trang theo con trỏ (created_at, id) nên chỉ mục phải đúng thứ tự này
CREATE INDEX ix_posts_feed   ON posts (created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_posts_author ON posts (author_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_posts_search ON posts USING gin (search_text gin_trgm_ops)
    WHERE deleted_at IS NULL AND visibility = 'PUBLIC';

CREATE TABLE post_media (
    id           UUID PRIMARY KEY,
    post_id      UUID         NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    kind         VARCHAR(10)  NOT NULL,
    url          VARCHAR(500) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    sort_order   SMALLINT     NOT NULL,
    CONSTRAINT chk_post_media_kind CHECK (kind IN ('IMAGE', 'VIDEO')),
    CONSTRAINT uq_post_media_order UNIQUE (post_id, sort_order)
);

-- Hashtag lưu dạng chuẩn hóa (không dấu, chữ thường). Bài viết bị xóa mềm thì lọc ở truy vấn.
CREATE TABLE post_hashtags (
    post_id UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    tag     VARCHAR(50) NOT NULL,
    PRIMARY KEY (post_id, tag)
);
CREATE INDEX ix_post_hashtags_tag ON post_hashtags (tag, post_id);
