-- ============================================================
--  V1__init.sql  –  Initial schema for Mock Server
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id           BIGSERIAL    PRIMARY KEY,
    username     VARCHAR(255) NOT NULL UNIQUE,
    assigned_port INTEGER     UNIQUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mock_definitions (
    id                      BIGSERIAL    PRIMARY KEY,
    owner_id                BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                    VARCHAR(255) NOT NULL,
    http_method             VARCHAR(10)  NOT NULL,          -- GET, POST, PUT, DELETE, PATCH, * (any)
    path_pattern            VARCHAR(500) NOT NULL,          -- Ant-style: /api/users/**
    request_body_contains   TEXT,                           -- optional body substring match
    response_status         INTEGER      NOT NULL DEFAULT 200,
    response_body           TEXT,
    response_content_type   VARCHAR(255) NOT NULL DEFAULT 'application/json',
    response_headers        TEXT,                           -- JSON map stored as text
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    priority                INTEGER      NOT NULL DEFAULT 0, -- higher = matched first
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mock_definitions_owner    ON mock_definitions(owner_id);
CREATE INDEX IF NOT EXISTS idx_mock_definitions_active   ON mock_definitions(owner_id, is_active);

CREATE TABLE IF NOT EXISTS request_logs (
    id               BIGSERIAL    PRIMARY KEY,
    user_port        INTEGER      NOT NULL,
    owner_id         BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    timestamp        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    method           VARCHAR(10)  NOT NULL,
    path             VARCHAR(2000) NOT NULL,
    query_params     TEXT,
    request_headers  TEXT,                               -- JSON map
    request_body     TEXT,
    content_type     VARCHAR(255),
    matched_mock_id  BIGINT       REFERENCES mock_definitions(id) ON DELETE SET NULL,
    response_status  INTEGER,
    response_body    TEXT,
    remote_addr      VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_request_logs_owner     ON request_logs(owner_id);
CREATE INDEX IF NOT EXISTS idx_request_logs_timestamp ON request_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_request_logs_port      ON request_logs(user_port);
