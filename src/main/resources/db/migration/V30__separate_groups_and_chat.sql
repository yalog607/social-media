-- V30: Tách biệt tính năng Hội nhóm (Groups) và Tin nhắn (Chat)
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS is_social_group BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS chat_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Cập nhật tất cả các nhóm hiện có thành Hội nhóm với chat_enabled = FALSE (chờ chủ nhóm mở)
UPDATE conversations
SET is_social_group = TRUE,
    chat_enabled = FALSE
WHERE type = 'GROUP';

-- Các cuộc trò chuyện 1-1 (DIRECT) luôn bật chat
UPDATE conversations
SET chat_enabled = TRUE
WHERE type = 'DIRECT';

