-- ALOUTE V1: tài khoản, vai trò, hồ sơ, refresh token, đặt lại mật khẩu

CREATE TABLE users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    username       VARCHAR(30)  NOT NULL,
    password_hash  VARCHAR(100),                       -- NULL với tài khoản chỉ đăng nhập Social
    auth_provider  VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    firebase_uid   VARCHAR(128),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'FACEBOOK')),
    CONSTRAINT chk_users_status   CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT chk_users_username CHECK (username ~ '^[a-z0-9_.]{3,30}$')
);
CREATE UNIQUE INDEX uq_users_email    ON users (lower(email));
CREATE UNIQUE INDEX uq_users_username ON users (lower(username));
CREATE UNIQUE INDEX uq_users_firebase ON users (firebase_uid) WHERE firebase_uid IS NOT NULL;

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_user_roles_role CHECK (role IN ('USER', 'CREATOR', 'MANAGER', 'ADMIN'))
);

CREATE TABLE profiles (
    user_id                 UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name            VARCHAR(50) NOT NULL,
    bio                     VARCHAR(300),
    avatar_url              VARCHAR(500),
    cover_url               VARCHAR(500),
    -- Quyền riêng tư: lưu sẵn từ GĐ1, áp dụng vào bài viết/chat ở GĐ2-3
    profile_visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    default_post_visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    message_permission      VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_profiles_pv  CHECK (profile_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE')),
    CONSTRAINT chk_profiles_dpv CHECK (default_post_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE')),
    CONSTRAINT chk_profiles_mp  CHECK (message_permission IN ('EVERYONE', 'FRIENDS', 'NOBODY'))
);

-- Chỉ lưu HASH (SHA-256) của token, không lưu token thô
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reset_tokens_user ON password_reset_tokens (user_id);
