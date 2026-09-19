-- Поля профиля, которые пишет сам игрок из интерфейса.
ALTER TABLE app_user
    ADD COLUMN active_room_id                    VARCHAR(80),
    ADD COLUMN active_room_updated_at            BIGINT,
    ADD COLUMN default_meme_loadout_updated_at   BIGINT,
    ADD COLUMN default_meme_loadout_confirmed_at BIGINT,
    ADD COLUMN last_login_at                     TIMESTAMPTZ;
