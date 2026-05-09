CREATE TABLE IF NOT EXISTS environment_packages (
    id          BIGSERIAL    PRIMARY KEY,
    owner_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id, name)
);

CREATE INDEX IF NOT EXISTS idx_environment_packages_owner
    ON environment_packages(owner_id);

CREATE TABLE IF NOT EXISTS environment_variables (
    id           BIGSERIAL    PRIMARY KEY,
    owner_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id   BIGINT       REFERENCES environment_packages(id) ON DELETE CASCADE,
    variable_key VARCHAR(255) NOT NULL,
    value        TEXT,
    description  TEXT,
    hidden       BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_environment_variables_owner
    ON environment_variables(owner_id);

CREATE INDEX IF NOT EXISTS idx_environment_variables_package
    ON environment_variables(package_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_environment_globals_owner_key
    ON environment_variables(owner_id, variable_key)
    WHERE package_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_environment_package_owner_key
    ON environment_variables(owner_id, package_id, variable_key)
    WHERE package_id IS NOT NULL;
