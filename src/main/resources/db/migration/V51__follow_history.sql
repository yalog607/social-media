-- The lock also prevents older application instances from inserting during backfill.
LOCK TABLE follows IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE follow_events (
    id UUID PRIMARY KEY,
    follower_id UUID NOT NULL REFERENCES users(id),
    followee_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_follow_events_followee_created ON follow_events (followee_id, created_at);

CREATE TABLE follow_history_metadata (recording_since TIMESTAMPTZ NOT NULL);
INSERT INTO follow_history_metadata (recording_since) VALUES (clock_timestamp());

INSERT INTO follow_events (id, follower_id, followee_id, created_at)
SELECT id, follower_id, followee_id, created_at FROM follows;

CREATE FUNCTION record_follow_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO follow_events (id, follower_id, followee_id, created_at)
    VALUES (NEW.id, NEW.follower_id, NEW.followee_id, NEW.created_at);
    RETURN NEW;
END;
$$;

CREATE TRIGGER follows_record_event AFTER INSERT ON follows
FOR EACH ROW EXECUTE FUNCTION record_follow_event();
