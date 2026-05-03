CREATE TABLE IF NOT EXISTS mock_collections (
    id          BIGSERIAL    PRIMARY KEY,
    owner_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id, name)
);

CREATE INDEX IF NOT EXISTS idx_mock_collections_owner ON mock_collections(owner_id);

-- Add optional collection reference to mock_definitions
ALTER TABLE mock_definitions
    ADD COLUMN IF NOT EXISTS collection_id BIGINT
    REFERENCES mock_collections(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_mock_definitions_collection ON mock_definitions(collection_id);
