-- Поля, которые клиент пишет при создании комнаты: счётчик игроков для
-- списка комнат и признак видеопровайдера. Без них запись комнаты
-- отвергалась как FIELD_UNKNOWN, и комната не создавалась.

ALTER TABLE room
    ADD COLUMN public_active_players INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN public_presence_at    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN video_provider        VARCHAR(24) DEFAULT 'livekit';
