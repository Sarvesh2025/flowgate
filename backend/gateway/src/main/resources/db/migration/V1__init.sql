-- Stub control-plane schema. JPA entities come in Phase 3.
-- Flyway is the source of truth (spring.jpa.hibernate.ddl-auto=none).

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    username        VARCHAR(64) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE routes (
    id              UUID PRIMARY KEY,
    route_key       VARCHAR(64) NOT NULL UNIQUE,
    path_pattern    VARCHAR(255) NOT NULL,
    methods         VARCHAR(64) NOT NULL,
    rewrite_prefix  VARCHAR(255),
    lb_algorithm    VARCHAR(32) NOT NULL DEFAULT 'ROUND_ROBIN',
    canary_percent  INT NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE upstream_targets (
    id              UUID PRIMARY KEY,
    route_id        UUID NOT NULL REFERENCES routes (id) ON DELETE CASCADE,
    url             VARCHAR(512) NOT NULL,
    weight          INT NOT NULL DEFAULT 1,
    canary          BOOLEAN NOT NULL DEFAULT FALSE,
    healthy         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE rate_limit_rules (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    route_id            UUID REFERENCES routes (id) ON DELETE CASCADE,
    scope               VARCHAR(32) NOT NULL,
    algorithm           VARCHAR(32) NOT NULL,
    limit_count         INT NOT NULL,
    window_ms           INT NOT NULL,
    priority            INT NOT NULL DEFAULT 0,
    redis_down_policy   VARCHAR(32) NOT NULL DEFAULT 'FAIL_CLOSED',
    quota_limit         INT,
    quota_period        VARCHAR(16),
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    actor           VARCHAR(128) NOT NULL,
    action          VARCHAR(64) NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    entity_id       VARCHAR(64),
    before_json     JSONB,
    after_json      JSONB,
    at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rate_limit_rules_route ON rate_limit_rules (route_id);
CREATE INDEX idx_audit_events_at ON audit_events (at DESC);
