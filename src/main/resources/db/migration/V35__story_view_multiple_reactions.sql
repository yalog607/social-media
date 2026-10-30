-- Migration V35: Mở rộng độ dài cột reaction trong story_views để lưu tất cả các cảm xúc người xem đã thả
ALTER TABLE story_views ALTER COLUMN reaction TYPE VARCHAR(500);
