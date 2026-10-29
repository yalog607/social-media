CREATE TABLE IF NOT EXISTS stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_type VARCHAR(16) NOT NULL, -- 'IMAGE', 'VIDEO', 'TEXT'
    media_url VARCHAR(500),
    caption VARCHAR(500),
    background_color VARCHAR(32) DEFAULT 'lavender',
    visibility VARCHAR(16) NOT NULL DEFAULT 'FRIENDS',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stories_user_expires ON stories(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_stories_expires_at ON stories(expires_at);

CREATE TABLE IF NOT EXISTS story_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_story_viewer UNIQUE (story_id, viewer_id)
);

CREATE INDEX IF NOT EXISTS idx_story_views_story ON story_views(story_id);
CREATE INDEX IF NOT EXISTS idx_story_views_viewer ON story_views(viewer_id);

