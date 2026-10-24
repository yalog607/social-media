CREATE TABLE IF NOT EXISTS daily_moods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    emoji VARCHAR(16) NOT NULL,
    mood_text VARCHAR(100) NOT NULL,
    color_theme VARCHAR(32) NOT NULL DEFAULT 'lemon',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_daily_moods_user_id ON daily_moods(user_id);
CREATE INDEX IF NOT EXISTS idx_daily_moods_expires_at ON daily_moods(expires_at);
