ALTER TABLE sticker_packs ADD COLUMN access_type VARCHAR(12) NOT NULL DEFAULT 'FREE' CHECK (access_type IN ('FREE', 'AWARD'));
ALTER TABLE sticker_packs ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED'));
ALTER TABLE sticker_packs ADD COLUMN ever_published BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE sticker_packs SET status = 'PUBLISHED', ever_published = TRUE WHERE id IN ('aloute-cat', 'aloute-frog', 'aloute-dog');
ALTER TABLE stickers ADD COLUMN keywords VARCHAR(300) NOT NULL DEFAULT '';
ALTER TABLE stickers ADD COLUMN render_type VARCHAR(10) NOT NULL DEFAULT 'LEGACY' CHECK (render_type IN ('LEGACY', 'ATLAS', 'IMAGE'));
UPDATE stickers SET render_type = 'ATLAS' WHERE pack_id IN ('aloute-cat', 'aloute-frog', 'aloute-dog');
ALTER TABLE user_sticker_packs ADD COLUMN grant_source VARCHAR(12) NOT NULL DEFAULT 'FREE' CHECK (grant_source IN ('FREE','AWARD'));
ALTER TABLE user_sticker_packs ADD COLUMN granted_by UUID REFERENCES users(id);
ALTER TABLE user_sticker_packs ADD COLUMN grant_reason VARCHAR(300);
ALTER TABLE user_sticker_packs ADD COLUMN revoked_at TIMESTAMPTZ;
ALTER TABLE user_sticker_packs ADD COLUMN revoked_by UUID REFERENCES users(id);
INSERT INTO user_sticker_packs (id,user_id,pack_id,installed_at)
SELECT gen_random_uuid(), q.user_id, s.pack_id, now() FROM (
 SELECT user_id,sticker_id FROM user_sticker_recent UNION SELECT user_id,sticker_id FROM user_sticker_favorites
) q JOIN stickers s ON s.id = q.sticker_id JOIN sticker_packs p ON p.id = s.pack_id
WHERE p.status = 'PUBLISHED' GROUP BY q.user_id,s.pack_id ON CONFLICT (user_id,pack_id) DO NOTHING;
ALTER TABLE notifications ADD COLUMN sticker_pack_id VARCHAR(50) REFERENCES sticker_packs(id);
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
 ('FRIEND_REQUEST','FRIEND_ACCEPTED','NEW_FOLLOWER','POST_REACTION','POST_COMMENT','COMMENT_REPLY',
  'POST_SHARED','POST_TAGGED','DONATION','BROADCAST','WARNING','MENTION','SUPPORT','REPORT',
  'GUESTBOOK_ENTRY','COMMENT_REACTION','STREAK','STICKER_REWARD'));
