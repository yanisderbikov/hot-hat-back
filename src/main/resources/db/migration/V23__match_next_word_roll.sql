-- SCRUM-25: persist the server's next-word draw for zero-delay client previews.
-- Kept private in the Room entity; only the current explainer receives
-- derived next-word candidates through authenticated TurnView responses.
ALTER TABLE room ADD COLUMN IF NOT EXISTS next_word_roll integer NOT NULL DEFAULT 0;
