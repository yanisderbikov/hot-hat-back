-- SCRUM-50: game-scoped character identity + owner-only paid smalltalk.
ALTER TABLE ai_voice_session
  ADD COLUMN IF NOT EXISTS mode varchar(16) NOT NULL DEFAULT 'game',
  ADD COLUMN IF NOT EXISTS speaker_uid varchar(160);
CREATE INDEX IF NOT EXISTS idx_ai_voice_smalltalk
  ON ai_voice_session(room_id,mode,status);
