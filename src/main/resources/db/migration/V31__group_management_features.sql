-- V31: Các tính năng nâng cao cho Hội nhóm (Cài đặt, nội quy, câu hỏi duyệt, phân quyền MODERATOR, ghim bài, báo cáo bài, đăng ẩn danh)

-- 1. Cho phép thêm vai trò MODERATOR vào conversation_members
ALTER TABLE conversation_members DROP CONSTRAINT IF EXISTS chk_conversation_members_role;
ALTER TABLE conversation_members ADD CONSTRAINT chk_conversation_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MODERATOR', 'MEMBER'));

-- 2. Thêm cột rules (nội quy) và approval_question (câu hỏi duyệt) vào conversations
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS rules VARCHAR(2000);
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS approval_question VARCHAR(500);

-- 3. Thêm cột answer (câu trả lời của người xin vào) vào conversation_requests
ALTER TABLE conversation_requests ADD COLUMN IF NOT EXISTS answer VARCHAR(500);

-- 4. Thêm cột is_anonymous vào posts (đăng bài ẩn danh trong nhóm)
ALTER TABLE posts ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

-- 5. Bảng group_post_reports (báo cáo bài đăng trong hội nhóm)
CREATE TABLE IF NOT EXISTS group_post_reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    post_id     UUID NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason      VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_group_post_reports_user UNIQUE (post_id, reporter_id)
);

CREATE INDEX IF NOT EXISTS ix_group_post_reports_group ON group_post_reports (group_id, created_at);
