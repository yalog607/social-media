-- ALOUTE V56: Series (chuỗi bài viết) của Creator. Một series gom nhiều bài của chính Creator; xóa series là xóa mềm
-- và không xóa các bài trong đó (posts.series_id được gỡ về NULL khi xóa).
CREATE TABLE series (
    id          UUID PRIMARY KEY,
    creator_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(80)  NOT NULL,
    description VARCHAR(300) NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX ix_series_creator ON series (creator_id, created_at DESC) WHERE deleted_at IS NULL;

ALTER TABLE posts ADD COLUMN series_id UUID REFERENCES series (id) ON DELETE SET NULL;
CREATE INDEX ix_posts_series ON posts (series_id, created_at) WHERE series_id IS NOT NULL;
