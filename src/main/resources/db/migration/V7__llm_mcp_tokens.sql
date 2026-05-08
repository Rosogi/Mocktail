CREATE TABLE IF NOT EXISTS llm_access_tokens (
    id                    BIGSERIAL    PRIMARY KEY,
    owner_id              BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash            VARCHAR(128) NOT NULL UNIQUE,
    token_preview         VARCHAR(64)  NOT NULL,
    request_logs_access   VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    mocks_access          VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    regenerated_at        TIMESTAMPTZ,
    last_used_at          TIMESTAMPTZ,
    UNIQUE (owner_id)
);

CREATE INDEX IF NOT EXISTS idx_llm_access_tokens_owner
    ON llm_access_tokens(owner_id);

CREATE INDEX IF NOT EXISTS idx_llm_access_tokens_hash
    ON llm_access_tokens(token_hash);
