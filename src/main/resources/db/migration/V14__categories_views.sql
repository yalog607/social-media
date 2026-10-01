-- ALOUTE V14: danh mục bài viết (do Manager quản lý) và lượt xem bài viết (cho Insights của Creator)

CREATE TABLE categories (
    id         UUID PRIMARY KEY,
    name       VARCHAR(40) NOT NULL,
    slug       VARCHAR(50) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

ALTER TABLE posts ADD COLUMN category_id UUID REFERENCES categories (id);
CREATE INDEX ix_posts_category ON posts (category_id, created_at DESC, id DESC)
    WHERE category_id IS NOT NULL AND deleted_at IS NULL AND scheduled_at IS NULL;

-- Mỗi người đã đăng nhập tính tối đa MỘT lượt xem cho mỗi bài mỗi ngày (UTC); khóa chính chống đếm trùng
CREATE TABLE post_views (
    post_id   UUID NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL REFERENCES users (id),
    day       DATE NOT NULL,
    PRIMARY KEY (post_id, viewer_id, day)
);
CREATE INDEX ix_post_views_day ON post_views (post_id, day);
