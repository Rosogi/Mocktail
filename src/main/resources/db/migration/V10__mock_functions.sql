CREATE TABLE IF NOT EXISTS mock_functions (
    id                  BIGSERIAL    PRIMARY KEY,
    owner_id            BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(120) NOT NULL,
    description         TEXT,
    signature_label     VARCHAR(500) NOT NULL,
    return_type         VARCHAR(50)  NOT NULL DEFAULT 'string',
    source_code         TEXT         NOT NULL,
    is_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    is_shared           BOOLEAN      NOT NULL DEFAULT FALSE,
    shared_at           TIMESTAMPTZ,
    revision            INTEGER      NOT NULL DEFAULT 1,
    read_only           BOOLEAN      NOT NULL DEFAULT FALSE,
    source_function_id  BIGINT       REFERENCES mock_functions(id) ON DELETE SET NULL,
    source_revision     INTEGER,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id, name)
);

CREATE INDEX IF NOT EXISTS idx_mock_functions_owner
    ON mock_functions(owner_id);

CREATE INDEX IF NOT EXISTS idx_mock_functions_shared
    ON mock_functions(is_shared, owner_id);

CREATE INDEX IF NOT EXISTS idx_mock_functions_source
    ON mock_functions(source_function_id);
