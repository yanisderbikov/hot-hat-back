-- Переключение областей media и app на таблицы схемы v2.
--
-- V17 завела двадцать девять таблиц трёх кластеров и не тронула ни одного
-- сценария. Этот файл переносит в них ту часть, у которой после переезда не
-- остаётся ни читателя, ни писателя в старых таблицах:
--
--   meme_library     → v2.meme + v2.meme_asset
--   feature_flag     → v2.feature_flag
--   analytics_event  → v2.analytics_event
--
-- Почему именно эти три, а не весь кластер разом. Правило то же, что в V16:
-- область переезжает вместе со своим фронтом. У библиотеки мемов адреса v2
-- есть целиком (/api/v2/media/memes, /api/v2/media/tickets/*,
-- /api/v2/media/files/**), и браузер переводится на них тем же шагом. У
-- записей партий (v2.recording и её восемь соседей) фронта на v2 нет:
-- app-core.js и admin.js ходят в /api/recordings и /api/recording-state,
-- поэтому таблицы записей остаются пустыми до своего шага.
--
-- Данные ПЕРЕНОСЯТСЯ: у библиотеки мемов есть живые строки, и потерять их
-- значит разрядить обоймы всем игрокам разом.


-- ─────────────── мост идентификаторов ───────────────
--
-- v2.meme.id — суррогат uuid, а сегодняшний «meme-9f31ab77c204» стал slug'ом.
-- Считается он той же формулой, что в V16 положила уuid'ы в обоймы:
-- md5('meme:' || id). Иначе обойма, собранная до переезда, показала бы на
-- несуществующий мем — а обойму собирали все.
--
-- Строки из meme_library мост уже знает (V16). Здесь добавляется встроенный
-- мем: строки в библиотеке у него никогда не было, а в обоймах он есть.
INSERT INTO v2.legacy_id_bridge (v2_id, kind, legacy_id)
VALUES (md5('meme:builtin-bmw-drugoy-ne-znayu')::uuid, 'meme', 'builtin-bmw-drugoy-ne-znayu')
ON CONFLICT DO NOTHING;

-- Автор мема и автор события: обратный перевод uuid → uid без таблицы
-- невозможен, md5 не обращается. Учётки, заведённые до переезда, положила
-- V14; здесь добираются те, кто успел уйти, но чьи мемы и события остались.
INSERT INTO v2.legacy_id_bridge (v2_id, kind, legacy_id)
SELECT DISTINCT md5('player:' || m.owner_uid)::uuid, 'player', m.owner_uid
FROM meme_library m
WHERE m.owner_uid IS NOT NULL AND btrim(m.owner_uid) <> '' AND length(m.owner_uid) <= 160
ON CONFLICT DO NOTHING;

INSERT INTO v2.legacy_id_bridge (v2_id, kind, legacy_id)
SELECT DISTINCT md5('player:' || e.uid)::uuid, 'player', e.uid
FROM analytics_event e
WHERE e.uid IS NOT NULL AND btrim(e.uid) <> '' AND length(e.uid) <= 160
ON CONFLICT DO NOTHING;


-- ─────────────── карточки мемов ───────────────
--
-- Переносятся не все строки, и это решение, а не потеря.
--
-- Мем без владельца И без файла в бакете остаётся в старой таблице. Такие
-- строки пришли из времён, когда ролик лежал data-URL'ом прямо в документе
-- или ссылкой на чужой хостинг: ни того, ни другого в схеме v2 нет (§6.3
-- выбросил data_url и src намеренно — карточка не должна уметь ссылаться на
-- чужой хост). Владельца им взять неоткуда: ограничение ck_meme_owner требует
-- его у всякого невстроенного мема, потому что из владельца строится путь
-- объекта и на нём же держится право на снятие.
--
-- Мем с владельцем, но без файла, переносится: карточка у него есть, ролика
-- нет, и это ровно тот случай, который MemeCardView описывает пустым
-- videoUrl. Он остаётся в библиотеке и в обоймах, а не исчезает молча.
--
-- Владелец берётся из колонки, а если её нет — из пути объекта: сервер сам
-- положил его туда, выдавая билет на загрузку.
INSERT INTO v2.meme (id, slug, title, duration_ms, division_language, origin, status,
                     owner_player_id, source_url, import_mode, optimized_version, optimized_at,
                     created_at, published_at, withdrawn_at)
SELECT md5('meme:' || m.id)::uuid,
       m.id,
       -- Название обязательно у всего, что не черновик: ck_meme_card. У
       -- безымянной строки оно собирается из хвоста идентификатора — так же,
       -- как его собирала сверка с бакетом.
       COALESCE(NULLIF(btrim(m.title), ''), 'Мем ' || right(m.id, 8)),
       -- ck_meme_duration: сотня миллисекунд снизу, десять секунд сверху.
       -- Столько же длится диверсия, и ролик длиннее перекрыл бы ход.
       LEAST(10000, GREATEST(100, COALESCE(m.duration_ms, 5000))),
       COALESCE(NULLIF(btrim(m.division_language), ''), 'ru'),
       CASE WHEN m.builtin THEN 'builtin'
            WHEN m.recovered_from_s3 THEN 'reconciled'
            ELSE 'player' END,
       -- Два старых статуса против трёх новых: 'disabled' — это снятый.
       -- Черновиков в старой таблице нет вовсе: там строка заводилась только
       -- вместе с публикацией.
       CASE WHEN m.status = 'disabled' THEN 'withdrawn' ELSE 'active' END,
       CASE WHEN m.builtin THEN NULL
            ELSE md5('player:' || COALESCE(NULLIF(btrim(m.owner_uid), ''),
                                           split_part(m.media_path, '/', 3)))::uuid END,
       m.source_url,
       m.import_mode,
       m.optimized_version,
       m.optimized_at,
       COALESCE(m.created_at, now()),
       -- ck_meme_published: у всего, что не черновик, дата публикации есть.
       COALESCE(m.created_at, now()),
       CASE WHEN m.status = 'disabled' THEN COALESCE(m.updated_at, now()) END
FROM meme_library m
WHERE m.builtin
   OR NULLIF(btrim(m.owner_uid), '') IS NOT NULL
   OR NULLIF(split_part(COALESCE(m.media_path, ''), '/', 3), '') IS NOT NULL
ON CONFLICT DO NOTHING;

-- Встроенный мем: строки в библиотеке у него не было никогда — список жил
-- константой в коде (GameRules.BUILTIN_MEMES и MemeLibraryAdapter). Ролик
-- лежит в статике сайта, поэтому файла в бакете у него нет и не будет, а
-- карточка нужна: без неё «есть ли такой мем» отвечает нет, и обойма
-- отказывается заряжаться тем, что лежит у каждого по умолчанию.
INSERT INTO v2.meme (id, slug, title, duration_ms, division_language, origin, status,
                     owner_player_id, created_at, published_at)
VALUES (md5('meme:builtin-bmw-drugoy-ne-znayu')::uuid,
        'builtin-bmw-drugoy-ne-znayu',
        'BMW — другой не знаю',
        5000, 'ru', 'builtin', 'active', NULL, now(), now())
ON CONFLICT DO NOTHING;


-- ─────────────── файлы мемов ───────────────
--
-- Строка появляется только у мема, чей объект действительно лежит в бакете:
-- состояние 'ready' означает «файл есть», и заводить его авансом нельзя.
--
-- Размер прижимается к пределу (ck_meme_asset_size: ролик 8 МиБ, заставка
-- 1 МиБ). В старых строках byte_size иногда больше: предел на загрузке
-- появился позже самих файлов. Прижимаем, а не отбрасываем строку — иначе
-- живой ролик остался бы без карточки.
--
-- DISTINCT ON по ключу объекта: ux_meme_asset_storage_key запрещает двум
-- мемам показывать на один файл, а в старой таблице это возможно —
-- перезаливка под чужим memeId ничем не проверялась.
INSERT INTO v2.meme_asset (meme_id, kind, storage_key, content_type, size_bytes,
                           state, upload_expires_at, uploaded_at, revision, created_at)
SELECT DISTINCT ON (media_path) meme_id, 'video', media_path, content_type, size_bytes,
       'ready', NULL, uploaded_at, revision, created_at
FROM (
    SELECT md5('meme:' || m.id)::uuid AS meme_id,
           m.media_path,
           COALESCE(NULLIF(btrim(m.mime), ''),
                    CASE WHEN m.media_path ILIKE '%.mp4' THEN 'video/mp4'
                         WHEN m.media_path ILIKE '%.ogv' THEN 'video/ogg'
                         ELSE 'video/webm' END)                    AS content_type,
           LEAST(8388608, GREATEST(0, COALESCE(m.byte_size, 0)))   AS size_bytes,
           COALESCE(m.created_at, now())                           AS uploaded_at,
           GREATEST(0, COALESCE(m.media_version, 0))               AS revision,
           COALESCE(m.created_at, now())                           AS created_at
    FROM meme_library m
    WHERE NULLIF(btrim(m.media_path), '') IS NOT NULL
) video
WHERE EXISTS (SELECT 1 FROM v2.meme x WHERE x.id = video.meme_id)
ON CONFLICT DO NOTHING;

-- Заставка. Размера у неё в старой строке нет вовсе — там одна колонка
-- byte_size на весь мем, и она про ролик. Ноль здесь честнее выдуманного
-- числа: ck_meme_asset_size его допускает, а показу он не мешает.
INSERT INTO v2.meme_asset (meme_id, kind, storage_key, content_type, size_bytes,
                           state, upload_expires_at, uploaded_at, revision, created_at)
SELECT DISTINCT ON (poster_path) meme_id, 'poster', poster_path, content_type, 0,
       'ready', NULL, uploaded_at, 0, created_at
FROM (
    SELECT md5('meme:' || m.id)::uuid AS meme_id,
           m.poster_path,
           CASE WHEN m.poster_path ILIKE '%.png'  THEN 'image/png'
                WHEN m.poster_path ILIKE '%.jpg'  THEN 'image/jpeg'
                WHEN m.poster_path ILIKE '%.jpeg' THEN 'image/jpeg'
                ELSE 'image/webp' END                AS content_type,
           COALESCE(m.created_at, now())             AS uploaded_at,
           COALESCE(m.created_at, now())             AS created_at
    FROM meme_library m
    WHERE NULLIF(btrim(m.poster_path), '') IS NOT NULL
) poster
WHERE EXISTS (SELECT 1 FROM v2.meme x WHERE x.id = poster.meme_id)
ON CONFLICT DO NOTHING;


-- ─────────────── переключатели возможностей ───────────────
--
-- Копия один в один плюс пустой updated_by: кто переключил флаг, старая
-- таблица не хранила вовсе. Перенос обязателен — флаги правят руками в базе,
-- и пустая таблица означала бы «всё выключено», то есть тест-боты и
-- админские возможности исчезли бы в момент выкатки.
INSERT INTO v2.feature_flag (name, enabled, description, updated_by, updated_at)
SELECT f.name, COALESCE(f.enabled, FALSE), f.description, NULL, COALESCE(f.updated_at, now())
FROM feature_flag f
WHERE length(f.name) <= 60
ON CONFLICT DO NOTHING;


-- ─────────────── продуктовые события ───────────────
--
-- Тело события разбирается из jsonb на шесть колонок — те самые, которые
-- считает отчёт. Значения прижимаются к границам ck_analytics_event_payload:
-- старый движок делал это молча при записи, и в базе могли остаться числа
-- любой величины.
--
-- Событие без игрока не переносится: player_id объявлен NOT NULL, потому что
-- все шесть видов событий считаются по игрокам, а строка без него в отчёт всё
-- равно не попадала. Почта не переносится намеренно (§6.3): она читается по
-- игроку, а копия в журнале пережила бы собственную смену.
--
-- Комната длиннее 24 знаков обнуляется: в v2 колонка объявлена по длине
-- настоящего идентификатора, а в старой таблице она VARCHAR(80), и туда
-- попадали склейки вида «hat-…-3».
INSERT INTO v2.analytics_event (event_key, event_type, player_id, room_id, room_name,
                                player_count, team_count, word_count, game_number,
                                duration_seconds, occurred_at)
SELECT e.event_key,
       e.event_type,
       md5('player:' || e.uid)::uuid,
       CASE WHEN length(COALESCE(e.room_id, '')) BETWEEN 1 AND 24 THEN e.room_id END,
       LEFT(NULLIF(btrim(COALESCE(e.payload ->> 'roomName', '')), ''), 80),
       -- Прижимаем только то, что действительно есть. GREATEST и LEAST в
       -- Postgres ПРОПУСКАЮТ NULL, поэтому без внешнего CASE отсутствующий
       -- счётчик превратился бы в ноль — то есть в партию из нуля игроков.
       CASE WHEN p.player_count     IS NOT NULL THEN LEAST(10,    GREATEST(0, p.player_count))::smallint END,
       CASE WHEN p.team_count       IS NOT NULL THEN LEAST(5,     GREATEST(0, p.team_count))::smallint END,
       CASE WHEN p.word_count       IS NOT NULL THEN LEAST(5000,  GREATEST(0, p.word_count))::int END,
       CASE WHEN p.game_number      IS NOT NULL THEN LEAST(10000, GREATEST(0, p.game_number))::int END,
       CASE WHEN p.duration_seconds IS NOT NULL THEN LEAST(86400, GREATEST(0, p.duration_seconds))::int END,
       COALESCE(e.created_at, now())
FROM analytics_event e
-- Числа достаются один раз и с проверкой формы: в jsonb мог оказаться текст,
-- и приведение упало бы на всей миграции из-за одной кривой строки.
CROSS JOIN LATERAL (
    SELECT CASE WHEN e.payload ->> 'playerCount'     ~ '^[0-9]{1,9}$'
                THEN (e.payload ->> 'playerCount')::numeric     END AS player_count,
           CASE WHEN e.payload ->> 'teamCount'       ~ '^[0-9]{1,9}$'
                THEN (e.payload ->> 'teamCount')::numeric       END AS team_count,
           CASE WHEN e.payload ->> 'wordCount'       ~ '^[0-9]{1,9}$'
                THEN (e.payload ->> 'wordCount')::numeric       END AS word_count,
           CASE WHEN e.payload ->> 'gameNumber'      ~ '^[0-9]{1,9}$'
                THEN (e.payload ->> 'gameNumber')::numeric      END AS game_number,
           CASE WHEN e.payload ->> 'durationSeconds' ~ '^[0-9]{1,9}$'
                THEN (e.payload ->> 'durationSeconds')::numeric END AS duration_seconds
) p
WHERE e.uid IS NOT NULL
  AND btrim(e.uid) <> ''
  AND e.event_type IN ('room_created', 'room_joined', 'game_started', 'game_finished',
                       'account_registered', 'account_login')
ON CONFLICT DO NOTHING;


-- ─────────────── что осталось в public и почему ───────────────
--
-- Таблицы meme_library, feature_flag и analytics_event после этой миграции не
-- читаются и не пишутся ни одним сценарием v2. Они НЕ удаляются здесь
-- намеренно, по той же причине, что колонки в V16: откат выкатки должен
-- находить свои данные на месте. Уйдут DROP-ом отдельной миграцией, когда
-- ветка отстоит на проде без откатов.
--
-- На них пока живут старые адреса /api/media, /api/features/{name},
-- /api/analytics и /api/db (коллекция memeLibrary) — их снос это отдельный шаг.
--
-- Кластер записей партий (v2.recording и восемь соседних таблиц) остаётся
-- пустым: его фронт ещё не переехал, и заполнять его сейчас значило бы
-- завести второе состояние той же записи.
