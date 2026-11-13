-- SCRUM-50: account for one GPT-Live voice session per AI-team turn.
-- Never store raw speech, transcripts, secret words or API credentials.
CREATE TABLE IF NOT EXISTS ai_voice_session (
    session_id varchar(80) PRIMARY KEY,
    room_id varchar(80) NOT NULL,
    game_number integer NOT NULL,
    team_id varchar(80) NOT NULL,
    bot_uid varchar(160) NOT NULL,
    turn_key varchar(160) NOT NULL,
    livekit_dispatch_id varchar(160),
    status varchar(32) NOT NULL DEFAULT 'pending',
    started_at timestamptz,
    finished_at timestamptz,
    last_usage_at timestamptz,
    voice_seconds numeric(12,3) NOT NULL DEFAULT 0,
    backend_input_tokens bigint NOT NULL DEFAULT 0,
    backend_output_tokens bigint NOT NULL DEFAULT 0,
    estimated_cost_usd numeric(14,6) NOT NULL DEFAULT 0,
    error_code varchar(120),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(room_id,game_number,turn_key)
);
CREATE INDEX IF NOT EXISTS idx_ai_voice_game
    ON ai_voice_session(room_id,game_number,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_voice_created
    ON ai_voice_session(created_at DESC);
