-- V29: Bổ sung group_id cho bài viết thuộc Hội nhóm
ALTER TABLE posts ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES conversations(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_posts_group_id ON posts(group_id);
