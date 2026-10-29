-- Persist the reference material and exact billable submission stage before
-- making any GenAPI request. This permits safe resumption after a Railway deploy.
ALTER TABLE neuro_replacement_job
  ADD COLUMN IF NOT EXISTS source_camera_key text,
  ADD COLUMN IF NOT EXISTS source_voice_key text,
  ADD COLUMN IF NOT EXISTS source_camera_mime varchar(40),
  ADD COLUMN IF NOT EXISTS source_voice_mime varchar(40),
  ADD COLUMN IF NOT EXISTS window_start_seconds double precision NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS submitting_model varchar(60);
CREATE INDEX IF NOT EXISTS idx_neuro_replacement_pending
  ON neuro_replacement_job(state, updated_at)
  WHERE state IN ('queued','processing');
