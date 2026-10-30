CREATE TABLE season_bans (
    season_id UUID NOT NULL REFERENCES seasons(id),
    user_id UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(1000) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (season_id, user_id)
);
