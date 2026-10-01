-- ALOUTE V11: hẹn giờ đăng bài. scheduled_at khác NULL nghĩa là bài CHƯA đăng; khi đến giờ bộ đăng bài đặt
-- scheduled_at = NULL và created_at = giờ hẹn (để bài nằm đúng chỗ trên bảng tin).
ALTER TABLE posts ADD COLUMN scheduled_at TIMESTAMPTZ;
CREATE INDEX ix_posts_scheduled ON posts (scheduled_at) WHERE scheduled_at IS NOT NULL AND deleted_at IS NULL;
