-- Своя авторизация вместо Firebase Auth.
--
-- uid остаётся строкой, а не bigserial: на него ссылаются два десятка таблиц
-- и он же ездит на фронтенд в составе комнат, чатов и рейтингов. Меняется
-- только источник значения — теперь его выдаём мы, а не Firebase.

ALTER TABLE app_user
    ADD COLUMN password_hash       VARCHAR(100),
    ADD COLUMN guest               BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN banned              BOOLEAN NOT NULL DEFAULT FALSE,
    -- Растёт при бане, смене пароля и выходе со всех устройств: любой ранее
    -- выданный access-токен с прежним номером перестаёт приниматься сразу,
    -- не дожидаясь истечения TTL.
    ADD COLUMN token_version       INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN password_updated_at TIMESTAMPTZ;

-- Гостей без почты может быть много, поэтому уникальность частичная.
CREATE UNIQUE INDEX ux_app_user_email_lower
    ON app_user (lower(email)) WHERE email IS NOT NULL;

-- Refresh-токены храним хешем: утечка таблицы не даёт войти ни за кого.
CREATE TABLE refresh_token (
    token_hash  VARCHAR(64)  PRIMARY KEY,
    uid         VARCHAR(160) NOT NULL REFERENCES app_user(uid) ON DELETE CASCADE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(200)
);
CREATE INDEX ix_refresh_token_uid ON refresh_token (uid);
CREATE INDEX ix_refresh_token_expires ON refresh_token (expires_at);

CREATE TABLE password_reset_token (
    token_hash VARCHAR(64)  PRIMARY KEY,
    uid        VARCHAR(160) NOT NULL REFERENCES app_user(uid) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_password_reset_uid ON password_reset_token (uid);
