CREATE TABLE IF NOT EXISTS known_remote_hosts (
    id           BIGSERIAL    PRIMARY KEY,
    owner_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    address      VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id, address)
);

CREATE INDEX IF NOT EXISTS idx_known_remote_hosts_owner
    ON known_remote_hosts(owner_id);

