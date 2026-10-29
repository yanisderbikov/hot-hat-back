-- Keep every paid attempt, including failed ones, instead of one row per room.
ALTER TABLE neuro_replacement_job DROP CONSTRAINT IF EXISTS neuro_replacement_job_room_id_key;
ALTER TABLE neuro_replacement_job ADD COLUMN IF NOT EXISTS final_video_key text;
CREATE INDEX IF NOT EXISTS idx_neuro_replacement_room_created
  ON neuro_replacement_job(room_id, created_at DESC);
