-- Migration V34: Bổ sung cột reaction vào bảng story_views để lưu cảm xúc trực tiếp trên story
ALTER TABLE story_views ADD COLUMN IF NOT EXISTS reaction VARCHAR(16);
