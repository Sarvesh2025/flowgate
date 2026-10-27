-- Store audit payloads as text. Hibernate binds String as varchar;
-- Postgres jsonb rejected that and rolled back rule CREATE.
ALTER TABLE audit_events
    ALTER COLUMN before_json TYPE TEXT USING before_json::text,
    ALTER COLUMN after_json TYPE TEXT USING after_json::text;
