CREATE TABLE IF NOT EXISTS auth_providers (
    id           BIGSERIAL    PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL
);

INSERT INTO auth_providers(code, display_name)
VALUES
    ('LDAP', 'LDAP'),
    ('DATABASE', 'Database'),
    ('STANDALONE', 'Standalone')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS roles (
    id           BIGSERIAL    PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description  TEXT
);

INSERT INTO roles(code, display_name, description)
VALUES
    ('ADMIN', 'Administrator', 'Can manage database-auth users.'),
    ('USER', 'User', 'Can manage own mocks and collections.')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS role_id BIGINT;

UPDATE users
SET display_name = username
WHERE display_name IS NULL;

UPDATE users
SET role_id = (SELECT id FROM roles WHERE code = 'USER')
WHERE role_id IS NULL;

ALTER TABLE users
    ALTER COLUMN display_name SET NOT NULL,
    ALTER COLUMN role_id SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_role
    FOREIGN KEY (role_id) REFERENCES roles(id);

CREATE TABLE IF NOT EXISTS user_identities (
    id                BIGSERIAL    PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    auth_provider_id   BIGINT       NOT NULL REFERENCES auth_providers(id),
    external_subject   VARCHAR(512) NOT NULL,
    login_hint         VARCHAR(255),
    CONSTRAINT uk_user_identities_provider_subject UNIQUE(auth_provider_id, external_subject)
);

INSERT INTO user_identities(user_id, auth_provider_id, external_subject, login_hint)
SELECT u.id,
       p.id,
       u.username,
       u.username
FROM users u
JOIN auth_providers p ON p.code = 'LDAP'
WHERE u.username IS NOT NULL
ON CONFLICT (auth_provider_id, external_subject) DO NOTHING;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;
ALTER TABLE users DROP COLUMN IF EXISTS username;

CREATE TABLE IF NOT EXISTS user_password_credentials (
    user_id               BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    login                 VARCHAR(255) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    password_changed_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_identities_user
    ON user_identities(user_id);

CREATE INDEX IF NOT EXISTS idx_users_role
    ON users(role_id);
