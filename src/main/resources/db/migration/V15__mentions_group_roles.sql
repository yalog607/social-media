-- ALOUTE V15: nhắc tên (@username) trong bình luận/nhóm chat, vai trò + biệt danh + ảnh nhóm

-- Thông báo "được nhắc tên": trong bình luận thì có post_id, trong nhóm chat thì có conversation_id
ALTER TABLE notifications ADD COLUMN conversation_id UUID REFERENCES conversations (id);
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
    ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
     'POST_SHARED', 'POST_TAGGED', 'DONATION', 'BROADCAST', 'WARNING', 'MENTION'));

-- Nhóm chat: ảnh đại diện nhóm, vai trò và biệt danh của từng thành viên
ALTER TABLE conversations ADD COLUMN avatar_url VARCHAR(500);
ALTER TABLE conversation_members ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE conversation_members ADD COLUMN nickname VARCHAR(40);
ALTER TABLE conversation_members ADD CONSTRAINT chk_conversation_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'));

-- Nhóm có sẵn: người tạo là chủ nhóm
UPDATE conversation_members m SET role = 'OWNER'
FROM conversations c
WHERE c.id = m.conversation_id AND c.type = 'GROUP' AND c.created_by = m.user_id;

-- Nhóm mà người tạo đã rời đi: người vào sớm nhất còn lại làm chủ nhóm
UPDATE conversation_members SET role = 'OWNER'
WHERE id IN (
    SELECT DISTINCT ON (m.conversation_id) m.id
    FROM conversation_members m JOIN conversations c ON c.id = m.conversation_id
    WHERE c.type = 'GROUP'
      AND NOT EXISTS (SELECT 1 FROM conversation_members o WHERE o.conversation_id = m.conversation_id AND o.role = 'OWNER')
    ORDER BY m.conversation_id, m.joined_at, m.id);
