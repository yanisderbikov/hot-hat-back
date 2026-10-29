-- Paid AI jobs must survive Railway restarts. Store references only,
-- never webcam/voice material or secret provider credentials.
CREATE TABLE IF NOT EXISTS neuro_replacement_job (
    job_id varchar(80) PRIMARY KEY,
    room_id varchar(80) NOT NULL UNIQUE,
    owner_uid varchar(160) NOT NULL,
    state varchar(32) NOT NULL,
    stage text,
    provider_requests jsonb NOT NULL DEFAULT '{}'::jsonb,
    final_video_url text,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_neuro_replacement_owner ON neuro_replacement_job(owner_uid, created_at DESC);
