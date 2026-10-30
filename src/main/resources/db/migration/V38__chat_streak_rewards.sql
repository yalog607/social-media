INSERT INTO avatar_frames (code, name, is_free) VALUES
 ('streak-10', 'Tia lửa tình bạn', false),
 ('streak-100', 'Ngọn lửa rực rỡ', false),
 ('streak-500', 'Vương miện lửa', false);

CREATE TABLE chat_streak_restores (
 conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
 missed_date DATE NOT NULL,
 restored_by UUID NOT NULL REFERENCES users(id),
 restored_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (conversation_id, missed_date)
);
CREATE INDEX ix_streak_restore_month ON chat_streak_restores (conversation_id, restored_at);
CREATE TABLE chat_streak_rewards (
 conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 milestone INTEGER NOT NULL,
 awarded_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (conversation_id, user_id, milestone)
);
CREATE TABLE chat_streak_reminders (
 conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 reminder_date DATE NOT NULL,
 PRIMARY KEY (conversation_id, user_id, reminder_date)
);
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
 ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
  'POST_SHARED', 'POST_TAGGED', 'DONATION', 'BROADCAST', 'WARNING', 'MENTION', 'SUPPORT', 'REPORT',
  'GUESTBOOK_ENTRY', 'COMMENT_REACTION', 'STREAK'));
