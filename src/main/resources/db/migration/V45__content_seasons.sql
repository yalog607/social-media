-- Times are stored as instants; the administration UI interprets dates in Asia/Ho_Chi_Minh.
CREATE TABLE seasons (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    required_hashtag VARCHAR(50) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'UPCOMING', 'ACTIVE', 'EVALUATING', 'SETTLED')),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    evaluated_at TIMESTAMPTZ,
    settled_at TIMESTAMPTZ,
    settled_by UUID REFERENCES users(id),
    CHECK (start_time < end_time)
);

CREATE TABLE season_posts (
    season_id UUID NOT NULL REFERENCES seasons(id),
    post_id UUID NOT NULL REFERENCES posts(id),
    author_id UUID NOT NULL REFERENCES users(id),
    published_at TIMESTAMPTZ NOT NULL,
    score BIGINT NOT NULL DEFAULT 0 CHECK (score >= 0),
    eligible BOOLEAN NOT NULL DEFAULT TRUE,
    excluded_at TIMESTAMPTZ,
    excluded_by UUID REFERENCES users(id),
    exclusion_reason TEXT,
    PRIMARY KEY (season_id, post_id)
);
CREATE INDEX ix_season_posts_author ON season_posts(season_id, author_id);

-- Append-only changes allow cutoff calculations even if an interaction is removed later.
CREATE TABLE season_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    season_id UUID NOT NULL REFERENCES seasons(id),
    post_id UUID NOT NULL REFERENCES posts(id),
    actor_id UUID REFERENCES users(id),
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('ELIGIBILITY', 'REACTION', 'COMMENT', 'SHARE')),
    source_id UUID NOT NULL,
    present BOOLEAN NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_season_events_cutoff ON season_events(season_id, post_id, kind, source_id, occurred_at, id);

CREATE TABLE season_leaderboard_snapshots (
    season_id UUID NOT NULL REFERENCES seasons(id),
    author_id UUID NOT NULL REFERENCES users(id),
    rank INTEGER NOT NULL CHECK (rank > 0),
    score BIGINT NOT NULL CHECK (score >= 0),
    first_post_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (season_id, author_id),
    UNIQUE (season_id, rank)
);
