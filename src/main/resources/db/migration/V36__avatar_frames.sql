CREATE TABLE avatar_frames (code VARCHAR(40) PRIMARY KEY, name VARCHAR(100) NOT NULL, is_free BOOLEAN NOT NULL DEFAULT FALSE);
INSERT INTO avatar_frames (code, name, is_free) VALUES
    ('pink', 'Hồng ngọt ngào', true), ('lavender', 'Tím mộng mơ', true),
    ('sunshine', 'Nắng rực rỡ', true), ('event-star', 'Ngôi sao sự kiện', false);
CREATE TABLE user_avatar_frames (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    frame_code VARCHAR(40) NOT NULL REFERENCES avatar_frames(code),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, frame_code)
);
ALTER TABLE profiles ADD COLUMN avatar_frame VARCHAR(40) REFERENCES avatar_frames(code);
