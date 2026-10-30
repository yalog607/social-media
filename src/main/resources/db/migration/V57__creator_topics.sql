-- ALOUTE V57: Chủ đề của Creator trên trang hồ sơ (tối đa 5, chọn từ danh mục có sẵn).

CREATE TABLE creator_topics (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    position    SMALLINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, category_id)
);
CREATE INDEX ix_creator_topics_user ON creator_topics (user_id, position);
