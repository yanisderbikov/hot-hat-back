-- Поля, которые клиент пишет, но которых нет в схеме. Строгая проверка
-- отвергает такую запись целиком (400 FIELD_UNKNOWN), поэтому ломается не
-- одно поле, а вся операция. Продолжение V3, причина та же.
--
-- room_player.video_provider: клиент шлёт его в heartbeat видеосвязи
-- (livekit.js:3175) вместе с last_seen_at, камерой и микрофоном. Без колонки
-- падал весь heartbeat, а не только этот признак.
ALTER TABLE room_player
    ADD COLUMN video_provider VARCHAR(24);

-- room_spectator.role и .preview: их пишет и вход зрителя (app-core.js:12373),
-- и превью комнаты на главной (live-preview.js:102). Без колонок зритель не
-- записывался вообще — ни один из двух путей не доходил до базы.
-- preview — признак «это лёгкая подписка ради превью», а не полноценный зритель.
ALTER TABLE room_spectator
    ADD COLUMN role    VARCHAR(24) DEFAULT 'spectator',
    ADD COLUMN preview BOOLEAN NOT NULL DEFAULT FALSE;

-- room_chat_message.attachment: картинка в чате комнаты (app-core.js:8478)
-- уходит объектом {kind, dataUrl, width, height, name}. Текстовые сообщения
-- проходили, картинки — нет. jsonb, потому что состав полей зависит от kind.
ALTER TABLE room_chat_message
    ADD COLUMN attachment JSONB;
