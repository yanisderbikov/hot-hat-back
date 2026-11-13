-- SCRUM-50: game-scoped Rublisha memory of confirmed server word outcomes.
-- Never stores unguessed words, voice recordings, user speech or model output.
CREATE TABLE IF NOT EXISTS ai_voice_word_memory (
 id varchar(64) PRIMARY KEY,
 room_id varchar(80) NOT NULL,
 game_number integer NOT NULL,
 team_id varchar(80) NOT NULL,
 turn_id varchar(120) NOT NULL,
 action_id varchar(80) NOT NULL,
 word varchar(160) NOT NULL,
 outcome varchar(16) NOT NULL CHECK (outcome IN ('guessed','skipped')),
 recorded_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT uq_ai_voice_word_action UNIQUE(room_id,game_number,turn_id,action_id)
);
CREATE INDEX IF NOT EXISTS idx_ai_voice_word_memory_recall
 ON ai_voice_word_memory(room_id,game_number,team_id,recorded_at DESC);
