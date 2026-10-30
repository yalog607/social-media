-- ALOUTE V46: bỏ ví Xu ảo, donate, mở khóa bài trả phí và huy hiệu fan
-- Chuyển mọi bài trả phí hiện có thành miễn phí; xóa toàn bộ dữ liệu ví và lịch sử giao dịch (owner đã chốt).

-- 1) Xóa thông báo DONATION trước khi siết lại CHECK constraint
DELETE FROM notifications WHERE type = 'DONATION';

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
    ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
     'POST_SHARED', 'POST_TAGGED', 'BROADCAST', 'WARNING', 'MENTION', 'SUPPORT', 'REPORT',
     'GUESTBOOK_ENTRY', 'COMMENT_REACTION', 'STREAK', 'STICKER_REWARD'));

-- 2) Broadcast audience FANS không còn tồn tại; quy đổi lịch sử về FOLLOWERS để giữ bản ghi hợp lệ
UPDATE broadcasts SET audience = 'FOLLOWERS' WHERE audience = 'FANS';
ALTER TABLE broadcasts DROP CONSTRAINT chk_broadcasts_audience;
ALTER TABLE broadcasts ADD CONSTRAINT chk_broadcasts_audience CHECK (audience IN ('FOLLOWERS'));

-- 3) Bài trả phí thành miễn phí: bỏ hẳn cột giá (không giữ cột chết)
ALTER TABLE posts DROP CONSTRAINT IF EXISTS chk_posts_unlock_price;
ALTER TABLE posts DROP COLUMN IF EXISTS unlock_price;

-- 4) Xóa toàn bộ dữ liệu và bảng ví (con trước, cha sau do khóa ngoại)
DROP TABLE IF EXISTS paid_content_unlocks;
DROP TABLE IF EXISTS wallet_transactions;
DROP TABLE IF EXISTS wallets;
