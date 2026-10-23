-- SCRUM-27: persist the start of an empty test session even after the
-- last human seat is deleted by the explicit leave operation.
ALTER TABLE room ADD COLUMN IF NOT EXISTS test_vacant_since_at bigint NOT NULL DEFAULT 0;
