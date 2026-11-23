-- SCRUM-65: prevents buying a new Wan clip while the required Immers RTX4090 worker is offline.
-- The worker updates this row only via its machine-token-validated HTTPS polling route.
CREATE TABLE IF NOT EXISTS neuro_gpu_worker_heartbeat (
    name varchar(64) PRIMARY KEY,
    updated_at timestamptz NOT NULL DEFAULT now()
);
