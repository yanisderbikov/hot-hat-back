-- Переключение области диверсий на таблицы схемы v2.
--
-- V15 завела тридцать две таблицы кластера и не тронула ни одного сценария:
-- новые таблицы остались пустыми, старые продолжили работать. Этот файл
-- переносит в них ту часть кластера, у которой ЕДИНСТВЕННЫЙ писатель — сервер,
-- и после которой в старых колонках не остаётся ни читателя, ни писателя:
--
--   app_user.sabotage_unlimited, app_user.sabotage_games_used
--                                    → v2.sabotage_entitlement
--   sabotage_game_use                → v2.sabotage_game_grant
--   app_user.default_meme_loadout    → v2.player_default_loadout
--
-- Почему именно эти три, а не весь кластер разом. Правило, проверенное на
-- команде: область переезжает вместе со своим фронтом, иначе расхождение
-- получается молчаливым. У квоты диверсий фронт уже на v2
-- (/api/v2/profile/me/sabotage-entitlement, home.js:132), у обоймы адрес есть
-- (/api/v2/profile/me/meme-loadout), и браузер переводится на него тем же
-- шагом. У комнаты и партии фронта на v2 нет вовсе: app-core.js играет через
-- /api/db/write прямо в public.room, и перенос сервера без него развёл бы одну
-- партию по двум хранилищам.
--
-- Данные ПЕРЕНОСЯТСЯ, а не заводятся заново, и это отличает файл от V15:
-- у бесплатных партий и обоймы есть живые значения, и потерять их — значит
-- подарить игроку пять партий и разрядить его арсенал.


-- ─────────────── мост идентификаторов: третий вид ───────────────
--
-- v2.player_default_loadout.meme_id объявлен как uuid (таблица v2.meme
-- появится вместе с кластером media), а сегодняшний мем — строка до 180
-- знаков: и «meme-<hex>» из библиотеки игрока, и «builtin-<slug>» у встроенных.
-- Мост V12 знал два вида, player и recording; здесь добавляется третий по той
-- же схеме и с тем же сроком жизни — он уйдёт, когда переедет media.
ALTER TABLE v2.legacy_id_bridge DROP CONSTRAINT ck_legacy_id_bridge_kind;
ALTER TABLE v2.legacy_id_bridge
    ADD CONSTRAINT ck_legacy_id_bridge_kind CHECK (kind IN ('player', 'recording', 'meme'));

-- Обратный перевод (uuid → идентификатор мема) нужен КАЖДОМУ чтению обоймы:
-- в ответе контракта стоят строковые идентификаторы, их ждёт экран арсенала.
-- Источников два, и второй не лишний: встроенные мемы строкой в meme_library
-- не лежат вовсе, а в обоймах игроков встречаются.
INSERT INTO v2.legacy_id_bridge (v2_id, kind, legacy_id)
SELECT md5('meme:' || id)::uuid, 'meme', id FROM meme_library
ON CONFLICT DO NOTHING;

INSERT INTO v2.legacy_id_bridge (v2_id, kind, legacy_id)
SELECT DISTINCT md5('meme:' || value)::uuid, 'meme', value
FROM app_user u, LATERAL jsonb_array_elements_text(u.default_meme_loadout) AS value
WHERE btrim(value) <> '' AND length(value) <= 180
ON CONFLICT DO NOTHING;


-- ─────────────── квота бесплатных партий ───────────────
--
-- Строка заводится каждому, у кого есть учётка в v2: право играть с
-- диверсиями принадлежит учётке, и «строки нет» здесь означало бы не «квота
-- не тронута», а «игрока не существует».
--
-- free_games_limit заполняется пятёркой — тем же числом, что стояло
-- константой FREE_SABOTAGE_GAMES в коде. Смысл переноса в том, что теперь это
-- значение строки: раздать одному человеку десять партий стало возможно без
-- выкатки.
--
-- LEAST(..., 5) не ставится намеренно: если в старой колонке накопилось
-- больше пяти, это израсходованные партии, и обрезка вернула бы их игроку.
INSERT INTO v2.sabotage_entitlement (player_id, unlimited, free_games_limit, free_games_used)
SELECT md5('player:' || u.uid)::uuid,
       COALESCE(u.sabotage_unlimited, FALSE),
       5,
       GREATEST(0, COALESCE(u.sabotage_games_used, 0))
FROM app_user u
WHERE EXISTS (SELECT 1 FROM v2.user_account a WHERE a.player_id = md5('player:' || u.uid)::uuid)
ON CONFLICT DO NOTHING;


-- ─────────────── журнал списаний ───────────────
--
-- Он же ключ идемпотентности: повторный старт той же партии не спишет вторую
-- бесплатную игру. Старый ключ — склейка «room-number-uid» в VARCHAR(200);
-- здесь та же тройка, но колонками, и уникальный индекс проверяет её база.
--
-- Строки без комнаты и с идентификатором не той формы не переносятся: в v2
-- room_id объявлен NOT NULL VARCHAR(24), а сама комната к этому времени давно
-- убрана. Счётчик израсходованного от этого не страдает — он приехал выше,
-- отдельной колонкой, а не пересчётом строк журнала.
INSERT INTO v2.sabotage_game_grant (player_id, room_id, game_number, consumed_at)
SELECT DISTINCT ON (md5('player:' || g.uid)::uuid, g.room_id, g.game_number)
       md5('player:' || g.uid)::uuid, g.room_id, g.game_number, g.created_at
FROM sabotage_game_use g
WHERE g.room_id IS NOT NULL
  AND length(g.room_id) <= 24
  AND EXISTS (SELECT 1 FROM v2.user_account a WHERE a.player_id = md5('player:' || g.uid)::uuid)
ORDER BY md5('player:' || g.uid)::uuid, g.room_id, g.game_number, g.created_at
ON CONFLICT DO NOTHING;


-- ─────────────── стартовая обойма ───────────────
--
-- Массив на пять элементов становится пятью строками. Порядок элементов —
-- это slot_index, и он значим: экран арсенала показывает обойму в том же
-- порядке, в каком её собрали.
--
-- Повторы схлопываются здесь, а не отвергаются: в старом массиве один мем мог
-- стоять дважды (проверки не было ни в браузере, ни на сервере), и такая
-- обойма означала четыре заряда вместо пяти. DISTINCT ON оставляет первое
-- вхождение, ровно как GameRules.normalizedLoadout при чтении.
--
-- Больше пяти не переносится: столько же брало и чтение.
INSERT INTO v2.player_default_loadout (player_id, slot_index, meme_id, updated_at)
SELECT player_id, slot_index, meme_id, updated_at
FROM (
    SELECT md5('player:' || u.uid)::uuid                                       AS player_id,
           (row_number() OVER (PARTITION BY u.uid ORDER BY picked.ord) - 1)::int AS slot_index,
           md5('meme:' || picked.value)::uuid                                  AS meme_id,
           COALESCE(to_timestamp(u.default_meme_loadout_updated_at / 1000.0), now()) AS updated_at
    FROM app_user u
    CROSS JOIN LATERAL (
        SELECT DISTINCT ON (element.value) element.value, element.ord
        FROM jsonb_array_elements_text(u.default_meme_loadout)
                 WITH ORDINALITY AS element(value, ord)
        WHERE btrim(element.value) <> '' AND length(element.value) <= 180
        ORDER BY element.value, element.ord
    ) picked
    WHERE EXISTS (SELECT 1 FROM v2.user_account a WHERE a.player_id = md5('player:' || u.uid)::uuid)
) numbered
WHERE slot_index <= 4
ON CONFLICT DO NOTHING;


-- ─────────────── что осталось в public и почему ───────────────
--
-- app_user.sabotage_unlimited, sabotage_games_used, default_meme_loadout,
-- default_meme_loadout_updated_at, default_meme_loadout_confirmed_at и таблица
-- sabotage_game_use после этой миграции не читаются и не пишутся ни одним
-- сценарием. Колонки НЕ удаляются здесь намеренно: откат выкатки должен
-- находить свои данные на месте. Они уйдут DROP-ом отдельной миграцией, когда
-- новая ветка отстоит на проде без откатов.
--
-- Остальной кластер комнаты и партии (тридцать таблиц V15) остаётся пустым:
-- его фронт ещё не переехал, и заполнять их сейчас значило бы завести второе
-- состояние той же партии.
