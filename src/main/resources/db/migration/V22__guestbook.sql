CREATE TABLE guestbook_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content VARCHAR(500) NOT NULL,
    color_theme VARCHAR(30) NOT NULL DEFAULT 'lemon',
    sticker_icon VARCHAR(50) DEFAULT '🌸',
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_guestbook_profile ON guestbook_entries(profile_user_id, is_pinned DESC, created_at DESC);
CREATE INDEX idx_guestbook_author ON guestbook_entries(author_id);
