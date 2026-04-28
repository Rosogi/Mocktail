CREATE TABLE IF NOT EXISTS user_settings (
    id         BIGSERIAL    PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key        VARCHAR(100) NOT NULL,
    value      TEXT,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id, key)
);

CREATE INDEX IF NOT EXISTS idx_user_settings_owner ON user_settings(owner_id);