-- SCRUM-32: Preserve the exact approved human assignment for safe restarts.
-- NULL is the original 15-second pilot; existing paid jobs retain their prompts and IDs.
ALTER TABLE neuro_replacement_job
  ADD COLUMN IF NOT EXISTS scene_assignment text;
