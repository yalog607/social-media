-- ALOUTE V10: ví Xu ảo, donate và nội dung trả phí của Creator

CREATE TABLE wallets (
    user_id          UUID PRIMARY KEY REFERENCES users (id),
    balance          BIGINT      NOT NULL DEFAULT 0,
    daily_claimed_on DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_wallets_balance CHECK (balance >= 0)
);

-- Sổ cái: mỗi thay đổi số dư là một dòng (amount luôn dương, loại giao dịch cho biết cộng hay trừ)
CREATE TABLE wallet_transactions (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id),
    type            VARCHAR(20) NOT NULL,
    amount          BIGINT      NOT NULL,
    counterparty_id UUID REFERENCES users (id),
    post_id         UUID REFERENCES posts (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_wallet_tx_type CHECK (type IN
        ('SIGNUP_BONUS', 'DAILY_BONUS', 'DONATE_SENT', 'DONATE_RECEIVED', 'UNLOCK_SPENT', 'UNLOCK_EARNED')),
    CONSTRAINT chk_wallet_tx_amount CHECK (amount > 0)
);
CREATE INDEX ix_wallet_tx_user ON wallet_transactions (user_id, created_at DESC);

-- Người đã mở khóa bài trả phí; UNIQUE chặn trả tiền hai lần cho cùng một bài
CREATE TABLE paid_content_unlocks (
    id         UUID PRIMARY KEY,
    post_id    UUID        NOT NULL REFERENCES posts (id),
    user_id    UUID        NOT NULL REFERENCES users (id),
    price      INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_unlocks_post_user UNIQUE (post_id, user_id)
);
CREATE INDEX ix_unlocks_user ON paid_content_unlocks (user_id);

ALTER TABLE posts ADD COLUMN unlock_price INT;
ALTER TABLE posts ADD CONSTRAINT chk_posts_unlock_price CHECK (unlock_price IS NULL OR unlock_price > 0);

ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (type IN
    ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'NEW_FOLLOWER', 'POST_REACTION', 'POST_COMMENT', 'COMMENT_REPLY',
     'POST_SHARED', 'POST_TAGGED', 'DONATION'));
