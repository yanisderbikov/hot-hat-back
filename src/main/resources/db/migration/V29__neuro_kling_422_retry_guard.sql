-- SCRUM-36: a definitive HTTP 422 may be retried only once per order.
-- The 31-Oct order was ALREADY retried twice via a live older deployment.
ALTER TABLE neuro_replacement_job
  ADD COLUMN IF NOT EXISTS kling_422_retry_count integer NOT NULL DEFAULT 0;

UPDATE neuro_replacement_job
   SET kling_422_retry_count = 2
 WHERE job_id = 'f3c3190a-8f68-4a38-95df-26dc3fe17f0e'
   AND state = 'failed'
   AND error_message LIKE 'GENAPI_HTTP_422%'
   AND NOT jsonb_exists(provider_requests, 'kling-video-o3');
