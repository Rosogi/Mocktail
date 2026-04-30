ALTER TABLE mock_collections
    ADD COLUMN IF NOT EXISTS is_shared BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS shared_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS read_only BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS source_collection_id BIGINT REFERENCES mock_collections(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS source_revision INTEGER;

CREATE INDEX IF NOT EXISTS idx_mock_collections_shared
    ON mock_collections(is_shared, owner_id);

CREATE INDEX IF NOT EXISTS idx_mock_collections_source
    ON mock_collections(source_collection_id);

CREATE TABLE IF NOT EXISTS collection_subscriptions (
    id                   BIGSERIAL   PRIMARY KEY,
    subscriber_id        BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_collection_id BIGINT      REFERENCES mock_collections(id) ON DELETE SET NULL,
    local_collection_id  BIGINT      NOT NULL REFERENCES mock_collections(id) ON DELETE CASCADE,
    source_revision      INTEGER     NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (subscriber_id, source_collection_id),
    UNIQUE (local_collection_id)
);

CREATE INDEX IF NOT EXISTS idx_collection_subscriptions_subscriber
    ON collection_subscriptions(subscriber_id);

CREATE INDEX IF NOT EXISTS idx_collection_subscriptions_source
    ON collection_subscriptions(source_collection_id);
