-- SCRUM-37. Opaque, expiring URL for GenAPI video inputs ending in .mp4.
-- Never expose an S3 signed query URL to the provider or make the bucket public.
CREATE TABLE IF NOT EXISTS neuro_kling_video_reference (
  access_token varchar(90) PRIMARY KEY,
  job_id varchar(80) NOT NULL,
  object_key text NOT NULL,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_neuro_kling_reference_expiry
  ON neuro_kling_video_reference(expires_at);
