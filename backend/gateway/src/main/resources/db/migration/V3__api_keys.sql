CREATE TABLE api_keys (
    id           UUID PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    prefix       VARCHAR(16) NOT NULL UNIQUE,
    key_hash     VARCHAR(64) NOT NULL,
    tier         VARCHAR(32) NOT NULL DEFAULT 'FREE',
    revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
