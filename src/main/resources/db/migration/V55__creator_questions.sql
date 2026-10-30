-- ALOUTE V55: Hỏi đáp công khai của Creator. Câu hỏi là một bài viết (posts.post_kind = 'QUESTION', đã có từ V54);
-- câu trả lời là bình luận. Chỉ cần thêm loại thông báo "Creator đặt câu hỏi mới" cho người theo dõi.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
    ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
     'POST_SHARED', 'POST_TAGGED', 'BROADCAST', 'WARNING', 'MENTION', 'SUPPORT', 'REPORT',
     'GUESTBOOK_ENTRY', 'COMMENT_REACTION', 'STREAK', 'STICKER_REWARD', 'SEASON_JOIN', 'SEASON_RESULT', 'SEASON_BANNED',
     'CREATOR_POLL', 'CREATOR_QUESTION'));
