-- Фича-флаги: включение и выключение возможностей без выкатки кода.
--
-- Флаг хранится строкой, а не колонкой на каждую фичу: добавление новой
-- не требует миграции, достаточно вставить строку.

CREATE TABLE feature_flag (
    name        VARCHAR(60)  PRIMARY KEY,
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    description VARCHAR(300),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO feature_flag (name, enabled, description) VALUES
    ('bot_enabled', FALSE, 'Тестовые видеоботы в комнате');
