-- V28: Bổ sung mô tả nhóm (description) cho bảng conversations
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS description VARCHAR(500);
