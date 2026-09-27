-- ALOUTE V5: kết bạn, theo dõi, chặn

-- Mỗi cặp bạn bè chỉ một hàng bất kể ai gửi lời mời trước: user_a_id luôn nhỏ hơn user_b_id.
-- requested_by cho biết ai là người gửi (để bên kia thấy nút Chấp nhận/Từ chối, còn người gửi thấy Hủy lời mời).
CREATE TABLE friendships (
    id            UUID PRIMARY KEY,
    user_a_id     UUID        NOT NULL REFERENCES users (id),
    user_b_id     UUID        NOT NULL REFERENCES users (id),
    status        VARCHAR(10) NOT NULL,
    requested_by  UUID        NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at  TIMESTAMPTZ,
    CONSTRAINT chk_friendships_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT chk_friendships_order CHECK (user_a_id < user_b_id),
    CONSTRAINT uq_friendships_pair UNIQUE (user_a_id, user_b_id)
);
CREATE INDEX ix_friendships_b ON friendships (user_b_id);

CREATE TABLE follows (
    id           UUID PRIMARY KEY,
    follower_id  UUID NOT NULL REFERENCES users (id),
    followee_id  UUID NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_follows_not_self CHECK (follower_id <> followee_id),
    CONSTRAINT uq_follows_pair UNIQUE (follower_id, followee_id)
);
CREATE INDEX ix_follows_followee ON follows (followee_id);

-- Chặn chỉ lưu một chiều (người chặn -> người bị chặn); mọi nơi kiểm tra quyền xem/nhắn tin đều
-- phải tự kiểm tra CẢ HAI chiều (BlockService#isBlockedEitherWay), vì hiệu lực của việc chặn là hai chiều.
CREATE TABLE blocks (
    id          UUID PRIMARY KEY,
    blocker_id  UUID NOT NULL REFERENCES users (id),
    blocked_id  UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_blocks_not_self CHECK (blocker_id <> blocked_id),
    CONSTRAINT uq_blocks_pair UNIQUE (blocker_id, blocked_id)
);
CREATE INDEX ix_blocks_blocked ON blocks (blocked_id);
