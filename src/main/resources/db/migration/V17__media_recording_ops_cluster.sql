-- Последние три кластера схемы v2 по §6 плана: media (мемы и их файлы),
-- recording (жизненный цикл записи партии) и ops — аналитика, расход,
-- тревоги, отчёты, обслуживание, флаги возможностей и прогоны тест-ботов.
-- Двадцать девять таблиц вместо сегодняшних одиннадцати (meme_library,
-- game_recording, analytics_event, feature_flag, maintenance_state,
-- usage_daily, usage_monthly, usage_alert, usage_report, usage_mail_counter,
-- usage_monitor_state) плюс семнадцать колонок про тест-ботов, разбросанных
-- по room и её соседям.
--
-- После этого файла в схеме v2 девяносто таблиц: восемьдесят девять из
-- девяноста двух по §6.2 плюс переходный мост v2.legacy_id_bridge, которого в
-- плане нет и который уйдёт вместе с последним непереехавшим кластером.
-- Незаведёнными остаются три: matchmaking_room, matchmaking_ticket и
-- matchmaking_ticket_member — кластер lobby, он не входит в эти три.
--
-- ─────────────────── Разрез по владельцу и по частоте записи ───────────────────
-- Тот же принцип, что в V13 и V15. Сегодняшние строки склеивают вещи с разным
-- писателем и разным ритмом:
--
--   meme_library      карточка мема (пишется дважды за жизнь: выложили и сняли)
--                     держит рядом media_path, poster_path, byte_size, mime,
--                     storage_provider, media_version — состояние ФАЙЛОВ в
--                     хранилище, у которого свой цикл: билет → загрузка →
--                     готов. Плюс data_url на несколько мегабайт прямо в
--                     строке: чтение библиотеки тянуло эти байты в память
--                     на каждую карточку.
--   game_recording    шестьдесят три колонки, в которых лежат сразу пять
--                     разных жизненных циклов: паспорт партии (пишется один
--                     раз), задание Egress (пишется вебхуками LiveKit),
--                     отметки страницы-рекордера (пишутся рекордером),
--                     файл в бакете (пишется по итогу выгрузки) и срок
--                     хранения со счётчиком сохранений (пишется игроками,
--                     каждым за себя). Все пятеро писали одну строку целиком.
--   usage_daily       один снимок расхода одним jsonb: двенадцать метрик и
--                     десять проверок живости внутри поля, по которому нельзя
--                     ни отобрать, ни отсортировать.
--   room.test_bot_*   расписание прогона ботов в строке комнаты на 103
--                     колонки — тик раз в секунду поверх всей комнаты.
--
-- Отсюда разрез: meme отдельно от meme_asset; паспорт recording отдельно от
-- recording_egress_job, recorder_session, recording_artifact и
-- recording_retention; снимок расхода — на usage_snapshot + строки
-- usage_metric_sample и service_health_probe; расписание ботов — своя таблица
-- test_bot_schedule с fillfactor 70.
--
-- ─────────────────── Почему схема v2 ───────────────────
-- Та же причина, что в V11, V13 и V15: целевые имена заняты сегодняшними
-- таблицами в public, и они продолжают работать — на них живут /api/media,
-- /api/recordings, /api/recording-state, /api/monitor и /api/test-bots.
-- Схема снимается одним ALTER TABLE ... SET SCHEMA в день, когда старые
-- таблицы уйдут.
--
-- ─────────────────── Данные не переносятся ───────────────────
-- Старые таблицы остаются как есть, новые пусты. Области не переключены:
-- этот шаг заводит схему и сущности, переезд кода делается вместе с переводом
-- фронта — правило, проверенное на команде и на диверсиях.
--
-- ─────────────────── Где здесь внешние ключи и где их нет ───────────────────
-- Внутри кластеров ключи расставлены все: мем → его файлы, запись → участники,
-- составы, задание Egress, артефакт, срок хранения, сохранения и доступы;
-- прогон ботов → боты и расписание; снимок расхода → метрики и проверки.
--
-- Наружу — три разряда колонок без ключа, ровно как в V15.
--   1. МЕСТО ЗА СТОЛОМ НЕ ССЫЛАЕТСЯ НА УЧЁТКУ: recording_participant.player_id
--      и test_bot.bot_player_id могут принадлежать тест-боту, а у бота учётки
--      нет и не будет.
--   2. АВТОРСТВО БЕЗ КЛЮЧА: started_by, updated_by, taken_by_player_id,
--      shared_by, leased_by — журнал обязан пережить удаление учётки.
--   3. ССЫЛКИ, КОТОРЫЕ НЕ ССЫЛКИ: maintenance_run_target.target_id хранит
--      идентификаторы четырёх разных сущностей строкой (см. таблицу).
--
-- Четыре ключа, отложенные V11 и V15 («ключи появятся вместе с таблицами»),
-- здесь по-прежнему не ставятся — и это самое неочевидное решение файла.
-- Причина проверена запуском, разбор в конце.

CREATE SCHEMA IF NOT EXISTS v2;


-- ═══════════════════════════ media ═══════════════════════════

-- Карточка мема.
--
-- id — суррогат uuid (§6.1), а сегодняшний «meme-9f31ab77c204» стал slug'ом.
-- Так надо, потому что этот текст выбирает КЛИЕНТ ещё до публикации: под него
-- уже выдан билет и по нему построен путь объекта в хранилище
-- (MemeAssetKey: memes/{дивизион}/{владелец}/{мем}/video.webm). Оставить его
-- первичным ключом значило бы дать клиенту право называть строку в базе.
--
-- status разложен на три значения, и среднего из них сегодня нет.
-- «draft» — это и есть БИЛЕТ на загрузку: §6.3 говорит, что отдельной таблицы
-- билетов не заводится, потому что билет — строка meme_asset в состоянии
-- pending со сроком. Но у строки файла первичный ключ (meme_id, kind), а
-- meme_id берётся из карточки; значит, выдача билета заводит карточку-черновик,
-- и только публикация даёт ей название, длительность и статус active.
-- Отсюда же @Version (§6.4): строка пишется не один раз — черновик, публикация,
-- оптимизация, снятие, — и писатели у этих шагов разные.
--
-- origin — одно перечисление вместо двух булевых флагов builtin и
-- recovered_from_s3. Флаги позволяли выразить бессмыслицу: встроенный мем,
-- восстановленный сверкой с бакетом.
--
-- title_search считает база: поиск по названию существует и сегодня, но живёт
-- в браузере (app-core.js:5820-5845) и требует, чтобы клиент выкачал всю
-- библиотеку целиком. Пока индекса нет, серверный поиск невозможен физически.
CREATE TABLE v2.meme (
    id                UUID         NOT NULL,
    -- Тот самый идентификатор, что стоит в пути объекта: meme-… или builtin-….
    slug              VARCHAR(180) NOT NULL,
    -- Пусто у черновика: название приходит с публикацией, а не с билетом.
    title             VARCHAR(120),
    -- Индекс полнотекстового поиска. 'simple', а не язык дивизиона: названия
    -- мемов девятиязычные и часто не на языке дивизиона вовсе, а
    -- стемминг чужого языка портит поиск сильнее, чем его отсутствие.
    title_search      TSVECTOR     GENERATED ALWAYS AS (to_tsvector('simple', coalesce(title, ''))) STORED,
    -- Столько же длится диверсия. Границы — MemeAssetLimits, второй рубеж.
    duration_ms       INTEGER,
    division_language VARCHAR(8)   NOT NULL DEFAULT 'ru',
    -- player | builtin | reconciled
    origin            VARCHAR(16)  NOT NULL DEFAULT 'player',
    -- draft | active | withdrawn
    status            VARCHAR(16)  NOT NULL DEFAULT 'draft',
    -- Владелец читается из пути объекта ровно так же, как его туда положил
    -- сервер, — поэтому у восстановленного сверкой мема он тоже есть.
    -- Пусто только у встроенных: их заводит посев, а не игрок.
    owner_player_id   UUID,
    -- Откуда нарезан ролик, если он пришёл по ссылке.
    source_url        VARCHAR(2048),
    -- Свободная пометка интерфейса: file, record, direct-url и подобное.
    import_mode       VARCHAR(24),
    optimized_version VARCHAR(40),
    optimized_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at      TIMESTAMPTZ,
    withdrawn_at      TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_meme PRIMARY KEY (id),
    CONSTRAINT ux_meme_slug UNIQUE (slug),
    CONSTRAINT ck_meme_origin CHECK (origin IN ('player', 'builtin', 'reconciled')),
    CONSTRAINT ck_meme_status CHECK (status IN ('draft', 'active', 'withdrawn')),
    -- Встроенный мем ничей, у остальных владелец обязателен: без него нельзя
    -- ни проверить право на снятие, ни построить путь файла.
    CONSTRAINT ck_meme_owner CHECK ((origin = 'builtin') = (owner_player_id IS NULL)),
    -- Карточка без названия и длительности бывает только черновиком.
    CONSTRAINT ck_meme_card
        CHECK (status = 'draft' OR (title IS NOT NULL AND duration_ms IS NOT NULL)),
    CONSTRAINT ck_meme_duration
        CHECK (duration_ms IS NULL OR duration_ms BETWEEN 100 AND 10000),
    CONSTRAINT ck_meme_published CHECK ((status = 'draft') = (published_at IS NULL)),
    CONSTRAINT ck_meme_withdrawn CHECK ((status = 'withdrawn') = (withdrawn_at IS NOT NULL))
);

-- Витрина библиотеки: страница дивизиона, свежие сверху. Частичный по
-- status='active' — снятые и черновики в витрину не попадают никогда, и
-- держать их в индексе незачем. Он же обслуживает сегодняшний
-- findByStatusNot('disabled', limit): отбор по дивизиону идёт первым
-- сегментом, а не после применения предела, как сейчас в браузере.
CREATE INDEX ix_meme_catalog
    ON v2.meme (division_language, created_at DESC) WHERE status = 'active';

-- Поиск по названию. GIN, а не btree: btree умеет сравнивать значения
-- целиком, а tsvector ищут по вхождению лексемы.
--
-- Отдельного индекса по владельцу здесь нет: запроса «мои мемы» не делает ни
-- один экран. Право на снятие проверяется у одной прочитанной карточки, а
-- признак mine в витрине считается сравнением с владельцем уже прочитанной
-- строки.
CREATE INDEX ix_meme_title_search ON v2.meme USING GIN (title_search);


-- Файл мема в хранилище: ролик или заставка.
--
-- Отдельная таблица, потому что у файла свой жизненный цикл, не совпадающий с
-- карточкой: билет выдан (pending, со сроком) → объект залит (ready) →
-- владелец перелил ролик заново (revision + 1). Сегодня всё это шесть колонок
-- внутри карточки, и перезаливка ролика переписывала карточку целиком вместе
-- с названием и статусом.
--
-- Билета как таблицы нет намеренно (§6.3): билет — это ровно строка в
-- состоянии pending со сроком upload_expires_at. Подписанная ссылка не
-- хранится: она выводится из ключа и живёт минуты.
--
-- storage_key уникален по всей таблице: два мема, показывающие на один
-- объект, означали бы, что снятие одного гасит ролик другого.
CREATE TABLE v2.meme_asset (
    meme_id           UUID         NOT NULL REFERENCES v2.meme (id) ON DELETE CASCADE,
    -- video | poster
    kind              VARCHAR(8)   NOT NULL,
    storage_key       VARCHAR(500) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL DEFAULT 0,
    -- pending | ready
    state             VARCHAR(16)  NOT NULL DEFAULT 'pending',
    upload_expires_at TIMESTAMPTZ,
    uploaded_at       TIMESTAMPTZ,
    -- Атомарный счётчик (§6.4): перелив ролика инкрементит его одним
    -- оператором. Сегодня это media_version, читаемый в память и записываемый
    -- обратно вместе со всей карточкой.
    revision          INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_meme_asset PRIMARY KEY (meme_id, kind),
    CONSTRAINT ux_meme_asset_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_meme_asset_kind CHECK (kind IN ('video', 'poster')),
    CONSTRAINT ck_meme_asset_state CHECK (state IN ('pending', 'ready')),
    CONSTRAINT ck_meme_asset_pending CHECK ((state = 'pending') = (upload_expires_at IS NOT NULL)),
    CONSTRAINT ck_meme_asset_ready CHECK ((state = 'ready') = (uploaded_at IS NOT NULL)),
    -- Пределы MemeAssetLimits: ролик 8 МиБ, заставка 1 МиБ. Заставка едет с
    -- каждой карточкой библиотеки, ролик — только когда его включили.
    CONSTRAINT ck_meme_asset_size CHECK (
        size_bytes >= 0
        AND (kind <> 'video'  OR size_bytes <= 8388608)
        AND (kind <> 'poster' OR size_bytes <= 1048576))
);

-- Сторож просроченных билетов: объект не залили, черновик надо убрать.
-- Частичный — у готовых файлов срока нет вовсе.
CREATE INDEX ix_meme_asset_pending
    ON v2.meme_asset (upload_expires_at) WHERE state = 'pending';


-- ═══════════════════════════ recording ═══════════════════════════

-- Паспорт записи партии: то, что не меняется после того, как партия сыграна.
--
-- id — суррогат uuid (§6.1). Сегодня ключ склеен из двух значений
-- («hat-…-3»), и по нему нельзя ни отобрать записи комнаты, ни отсортировать
-- партии по номеру, не разрезая строку.
--
-- Замороженные условия партии (game_mode, ranked, private_room, test_room,
-- оба языка, title) скопированы намеренно и не подпадают под §6.3: запись
-- переживает комнату, а её карточка обязана показывать ту партию, которая
-- на видео, а не сегодняшнее состояние комнаты. По той же причине room_id
-- обнуляется, а не уносит запись за собой.
--
-- Статуса здесь нет вовсе. Сегодня их два и они спорят: строковый status
-- рядом с числовым livekit_status. §6.3 сводит их в одно
-- recording_egress_job.state, а «файл удалён» — это recording_artifact.deleted_at.
-- Счёта победителя и суммы очков тоже нет: они выводятся из recording_team,
-- денормализованному счётчику здесь не с чем расходиться.
CREATE TABLE v2.recording (
    id                UUID        NOT NULL,
    -- SET NULL: комнату уберёт уборка, а запись живёт своим сроком.
    room_id           VARCHAR(24) REFERENCES v2.room (id) ON DELETE SET NULL,
    game_number       INTEGER     NOT NULL,
    -- Снимок названия комнаты. Если названия не было, подпись строит клиент
    -- из идентификатора — колонка остаётся честно пустой.
    title             VARCHAR(80),
    game_mode         VARCHAR(16) NOT NULL DEFAULT 'classic',
    ranked            BOOLEAN     NOT NULL DEFAULT FALSE,
    private_room      BOOLEAN     NOT NULL DEFAULT FALSE,
    test_room         BOOLEAN     NOT NULL DEFAULT FALSE,
    division_language VARCHAR(8)  NOT NULL DEFAULT 'ru',
    game_language     VARCHAR(8)  NOT NULL DEFAULT 'ru',
    word_count        INTEGER     NOT NULL DEFAULT 0,
    -- Кто нажал «снимать». Без ключа: журнал переживает удаление учётки.
    started_by        UUID        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_recording PRIMARY KEY (id),
    -- Ключ идемпотентности старта: две вкладки хозяина не заведут две записи
    -- одной партии. Сегодня это держалось склейкой строк в идентификаторе.
    CONSTRAINT ux_recording_game UNIQUE (room_id, game_number),
    CONSTRAINT ck_recording_game_number CHECK (game_number > 0),
    CONSTRAINT ck_recording_word_count CHECK (word_count >= 0)
);

-- Админский каталог: findAllByOrderByStartedAtMsDesc с пределом.
CREATE INDEX ix_recording_recent ON v2.recording (created_at DESC);
-- Тот же каталог с отбором по дивизиону: AdminRecordingQueryDTO.division.
CREATE INDEX ix_recording_division ON v2.recording (division_language, created_at DESC);


-- Кто играл в записанной партии.
--
-- Заменяет два jsonb сразу: participants (список объектов) и participant_uids
-- (те же uid списком строк, заведённые только ради поиска «я участник»).
-- Ник — снимок: игрок мог сменить его после партии, а на видео звучит старый.
-- Это единственная копия чужих данных, которую §6.3 оставляет, и оставляет
-- по той же причине, по которой оставлены условия партии.
--
-- Ключа на учётку нет: в записи мог играть тест-бот, а у бота учётки нет.
CREATE TABLE v2.recording_participant (
    recording_id UUID        NOT NULL REFERENCES v2.recording (id) ON DELETE CASCADE,
    player_id    UUID        NOT NULL,
    nickname     VARCHAR(20) NOT NULL,
    -- Команда, за которую играл; пусто — остался без команды.
    room_team_id UUID,
    bot          BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_recording_participant PRIMARY KEY (recording_id, player_id)
);

-- «Записи, в которых я играл»: право смотреть даётся участнику.
CREATE INDEX ix_recording_participant_player ON v2.recording_participant (player_id);


-- Составы и счёт команд записанной партии.
--
-- Победа — признак у команды, а не два параллельных массива winner_team_ids и
-- winner_team_names рядом со списком команд. Массивы приходилось сшивать по
-- идентификатору на клиенте, и при ничьей сшивка расходилась.
-- Состава списком здесь нет: он выводится из recording_participant.room_team_id.
CREATE TABLE v2.recording_team (
    recording_id   UUID        NOT NULL REFERENCES v2.recording (id) ON DELETE CASCADE,
    room_team_id   UUID        NOT NULL,
    name           VARCHAR(40) NOT NULL,
    -- Рейтинговая команда, которой играл этот состав; пусто — партия не
    -- рейтинговая. Без ключа: роспуск команды не должен трогать запись.
    ranked_team_id UUID,
    score          INTEGER     NOT NULL DEFAULT 0,
    is_winner      BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_recording_team PRIMARY KEY (recording_id, room_team_id)
);


-- Жизненный цикл задания Egress.
--
-- Всё, что в game_recording относилось к LiveKit: egress_id, livekit_status,
-- start_lock_at_ms, last_stop_attempt_at_ms, last_egress_sync_at_ms,
-- egress_active_at_ms, egress_ended_at_ms, start_error, egress_error.
-- Девять колонок, которые писали вебхуки и опрос, — в одной строке с
-- паспортом партии и со сроком хранения.
--
-- state — единственный статус записи наружу (§6.3). Сырой код LiveKit
-- сохранён только в журнале вебхуков: игроку он не нужен, а наш набор стадий
-- от него не зависит.
--
-- @Version (§6.4) и никакой колонки-замка: сегодня захват старта выражен
-- start_lock_at_ms, то есть «время, до которого чужой старт считается
-- недавним». Строка берётся SELECT … FOR UPDATE, внешний вызов LiveKit идёт
-- ВНЕ транзакции, результат применяется второй короткой транзакцией по
-- версии — это ответ на находку B5, где транзакция держалась поверх сетевого
-- вызова с таймаутом двадцать секунд при пуле в десять соединений.
CREATE TABLE v2.recording_egress_job (
    recording_id        UUID         NOT NULL REFERENCES v2.recording (id) ON DELETE CASCADE,
    -- Пусто, пока LiveKit не ответил на запрос старта.
    egress_id           VARCHAR(120),
    -- starting | active | processing | complete | failed
    state               VARCHAR(16)  NOT NULL DEFAULT 'starting',
    start_requested_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    active_at           TIMESTAMPTZ,
    stop_requested_at   TIMESTAMPTZ,
    ended_at            TIMESTAMPTZ,
    -- Когда последний раз сверялись с LiveKit опросом (ListEgress).
    last_sync_at        TIMESTAMPTZ,
    failure_reason      VARCHAR(600),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_recording_egress_job PRIMARY KEY (recording_id),
    CONSTRAINT ux_recording_egress_job_egress UNIQUE (egress_id),
    CONSTRAINT ck_recording_egress_job_state
        CHECK (state IN ('starting', 'active', 'processing', 'complete', 'failed')),
    -- Провал обязан назвать причину: сегодня она разъехалась по двум колонкам
    -- (start_error и egress_error), и какая из них заполнена — зависело от
    -- того, на каком шаге сорвалось.
    CONSTRAINT ck_recording_egress_job_failure
        CHECK ((state = 'failed') = (failure_reason IS NOT NULL))
);

-- Сторож незавершённых заданий: опрос LiveKit добирает те, по которым вебхук
-- не пришёл. Частичный — завершённые задания сторожа не интересуют.
CREATE INDEX ix_recording_egress_job_unfinished
    ON v2.recording_egress_job (state, start_requested_at)
    WHERE state IN ('starting', 'active', 'processing');


-- Журнал уведомлений LiveKit.
--
-- Второй и последний jsonb на всю базу (§6): тело вебхука задаёт внешняя
-- система, и раскладывать его по колонкам значило бы обещать, что чужой
-- контракт не пополнится. Мы читаем из него ровно четыре значения — они
-- вынесены в колонки рядом, а payload остаётся сырым ради разбора инцидентов.
--
-- delivery_id — идентификатор доставки; при его отсутствии в теле его место
-- занимает хеш тела. Сегодня повтор доставки применяется второй раз, потому
-- что отличить его не по чему: уникальный индекс — и есть починка.
CREATE TABLE v2.recording_egress_event (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY,
    delivery_id  VARCHAR(120) NOT NULL,
    -- Пусто, если уведомление не удалось привязать к записи: журнал ведётся
    -- и для таких, иначе разбирать нечего.
    recording_id UUID         REFERENCES v2.recording (id) ON DELETE CASCADE,
    event_name   VARCHAR(60)  NOT NULL,
    egress_id    VARCHAR(120),
    -- Сырой код LiveKit: EGRESS_COMPLETE и прочие. Дальше журнала не идёт.
    egress_state VARCHAR(40),
    -- Уведомление изменило состояние записи. false — повтор, чужая выгрузка
    -- или событие не про Egress: EgressWebhookOutcome называет все случаи.
    applied      BOOLEAN      NOT NULL DEFAULT FALSE,
    payload      JSONB        NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_recording_egress_event PRIMARY KEY (id),
    CONSTRAINT ux_recording_egress_event_delivery UNIQUE (delivery_id)
);

-- Разбор инцидента: все уведомления одной записи, свежие сверху.
CREATE INDEX ix_recording_egress_event_recording
    ON v2.recording_egress_event (recording_id, received_at DESC);


-- Отметки страницы-рекордера.
--
-- Заменяет шесть колонок game_recording: recorder_ready_at_ms,
-- recorder_ready_phase, recorder_start_signal_at_ms, recorder_start_phase,
-- recorder_livekit_identity, prewarmed. Писатель у них один и чужой всем
-- остальным — сама страница рекордера, машинная поверхность.
--
-- Три сегодняшних мутирующих GET (ready=1, started=1, ceremony_done=1) стали
-- POST-сигналами, но в базе это по-прежнему три отметки времени: важно не
-- «сколько раз позвали», а «дошёл ли рекордер до этого шага».
CREATE TABLE v2.recorder_session (
    recording_id          UUID         NOT NULL REFERENCES v2.recording (id) ON DELETE CASCADE,
    -- Съёмку подняли заранее, до старта партии.
    prewarm               BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Личность рекордера в комнате LiveKit: по ней запись отличает свою
    -- дорожку от участников.
    livekit_identity      VARCHAR(220),
    ready_at              TIMESTAMPTZ,
    -- Фаза комнаты в момент сигнала — она нужна только диагностике «на каком
    -- шаге рекордер догнал партию».
    ready_phase           VARCHAR(24),
    start_signal_at       TIMESTAMPTZ,
    start_phase           VARCHAR(24),
    ceremony_completed_at TIMESTAMPTZ,
    opened_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_recorder_session PRIMARY KEY (recording_id),
    -- Порядок сигналов не может нарушиться: снимать нельзя раньше готовности,
    -- церемония не кончается раньше первого кадра.
    CONSTRAINT ck_recorder_session_order CHECK (
        (start_signal_at IS NULL OR ready_at IS NOT NULL)
        AND (ceremony_completed_at IS NULL OR start_signal_at IS NOT NULL))
);

-- Рекордер подключился, но снимать не начал: это и есть зависшая съёмка.
CREATE INDEX ix_recorder_session_stuck
    ON v2.recorder_session (ready_at) WHERE start_signal_at IS NULL;


-- Файл записи в хранилище.
--
-- Отдельно от паспорта, потому что появляется позже всех и от другого
-- писателя: путь, размер и длительность приезжают в теле вебхука о завершении
-- выгрузки. deleted_at — мягкое удаление: строка записи переживает свой файл,
-- иначе ссылка на запись в личной переписке указывала бы в пустоту.
CREATE TABLE v2.recording_artifact (
    recording_id   UUID         NOT NULL REFERENCES v2.recording (id) ON DELETE CASCADE,
    object_path    VARCHAR(500) NOT NULL,
    storage_bucket VARCHAR(120),
    size_bytes     BIGINT       NOT NULL DEFAULT 0,
    -- Наносекунды: так длительность отдаёт LiveKit, и так её ждёт карточка.
    duration_ns    BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT pk_recording_artifact PRIMARY KEY (recording_id),
    CONSTRAINT ux_recording_artifact_object UNIQUE (object_path),
    CONSTRAINT ck_recording_artifact_size CHECK (size_bytes >= 0 AND duration_ns >= 0)
);


-- Срок хранения и счётчик сохранений.
--
-- Своя строка, потому что пишут её игроки — каждый, кто кладёт запись к себе,
-- — а не тот, кто снимал. Сегодня это колонки expires_at_ms и saved_count
-- внутри строки на шестьдесят три колонки: сохранение записи переписывало
-- вместе с собой состояние Egress.
--
-- save_count — атомарный счётчик (§6.4): UPDATE … SET save_count = save_count + 1
-- без чтения в память. Сегодня их два (saved_count и длина массива saved_by),
-- и они расходятся при одновременном сохранении двумя игроками.
CREATE TABLE v2.recording_retention (
    recording_id UUID        NOT NULL REFERENCES v2.recording (id) ON DELETE CASCADE,
    -- Пусто — запись сохранена и хранится, пока её не заберут из библиотеки.
    expires_at   TIMESTAMPTZ,
    save_count   INTEGER     NOT NULL DEFAULT 0,
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_recording_retention PRIMARY KEY (recording_id),
    CONSTRAINT ck_recording_retention_save_count CHECK (save_count >= 0),
    -- Сохранённая запись не имеет срока, несохранённая обязана его иметь:
    -- запись без срока и без сохранивших жила бы в бакете вечно.
    CONSTRAINT ck_recording_retention_expiry CHECK ((save_count = 0) = (expires_at IS NOT NULL))
);

-- Прогон уборки: просроченные и никем не сохранённые. Частичный — сохранённые
-- в него не попадают вовсе, а их со временем становится большинство.
CREATE INDEX ix_recording_retention_expired
    ON v2.recording_retention (expires_at) WHERE save_count = 0;


-- Кто положил запись к себе.
--
-- Заменяет jsonb-массив saved_by. Массив приходилось искать оператором
-- @> по всей таблице (findSavedBy, нативный запрос), и с ним же расходился
-- счётчик saved_count.
CREATE TABLE v2.recording_save (
    recording_id UUID        NOT NULL REFERENCES v2.recording (id) ON DELETE CASCADE,
    player_id    UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    saved_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_recording_save PRIMARY KEY (recording_id, player_id)
);

-- Личная библиотека записей: свежие сохранения сверху.
CREATE INDEX ix_recording_save_player ON v2.recording_save (player_id, saved_at DESC);


-- Кому запись открыта, кроме участников.
--
-- Заменяет jsonb-массив shared_with. Право смотреть складывается из трёх
-- источников: участник партии, сохранивший её у себя и тот, кому её
-- отправили в личной переписке; третий — это строка здесь.
CREATE TABLE v2.recording_share (
    recording_id UUID        NOT NULL REFERENCES v2.recording (id) ON DELETE CASCADE,
    grantee      UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    -- Кто открыл доступ; без ключа: доступ переживает удаление учётки автора.
    shared_by    UUID        NOT NULL,
    shared_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_recording_share PRIMARY KEY (recording_id, grantee)
);

-- «Что мне открыли»: проверка права на просмотр идёт от человека.
CREATE INDEX ix_recording_share_grantee ON v2.recording_share (grantee);


-- Включена ли запись партий в комнате.
--
-- Единственная таблица кластера, живущая при комнате, а не при записи, и это
-- разрешение расхождения §6.1: колонки record_game,
-- recording_preference_updated_at и recording_preference_updated_by убраны из
-- room, потому что флаг ставит хозяин комнаты РАДИ ЗАПИСИ, и писатель у него
-- — домен recording. Пока флаг лежал в room, его переписывало любое
-- обновление комнаты.
CREATE TABLE v2.room_recording_policy (
    room_id    VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Кто трогал флаг последним; без ключа, это авторство.
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_room_recording_policy PRIMARY KEY (room_id)
);

-- Отдельного индекса нет: флаг читается только по своей комнате, то есть по
-- первичному ключу. Частичный индекс по той же колонке (§6.2 называет
-- «(room_id) WHERE enabled») отвечал бы на вопрос «в каких комнатах запись
-- включена», а его не задаёт ни один сценарий.


-- ═══════════════════════════ ops · app ═══════════════════════════

-- Продуктовое событие клиента.
--
-- payload разобран на шесть колонок — ровно те шесть полей, которые описывает
-- AnalyticsEventPayloadView и считает дашборд. Сегодня это jsonb, и статистика
-- периода достаёт из него playerCount, teamCount и wordCount разбором json в
-- памяти на каждое из десяти тысяч прочитанных событий.
--
-- id — суррогат, а event_key — ключ идемпотентности: обработчик конца партии
-- срабатывает у каждого участника, и одно событие приезжает пять раз. Сегодня
-- ключ КЛИЕНТА стоит первичным, то есть строку в таблице называет браузер.
--
-- email здесь нет. Сегодня событие несёт и uid, и почту, хотя почта нужна
-- только чтобы показать её в отчёте: она читается через ProfileDirectoryPort
-- по player_id, а копия почты в журнале событий пережила бы её смену.
CREATE TABLE v2.analytics_event (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,
    event_key        VARCHAR(160) NOT NULL,
    -- room_created | room_joined | game_started | game_finished
    -- account_registered | account_login — набор закрыт AnalyticsEventType.
    event_type       VARCHAR(40)  NOT NULL,
    -- Событие всегда чьё-то: гостю аналитика не открыта. Без ключа — журнал
    -- переживает удаление учётки.
    player_id        UUID         NOT NULL,
    -- Пусто у событий учётки: у входа в неё комнаты нет.
    room_id          VARCHAR(24),
    room_name        VARCHAR(80),
    player_count     SMALLINT,
    team_count       SMALLINT,
    word_count       INTEGER,
    game_number      INTEGER,
    duration_seconds INTEGER,
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_analytics_event PRIMARY KEY (id),
    CONSTRAINT ux_analytics_event_key UNIQUE (event_key),
    CONSTRAINT ck_analytics_event_type CHECK (event_type IN (
        'room_created', 'room_joined', 'game_started', 'game_finished',
        'account_registered', 'account_login')),
    -- Границы те же, что проверяет DTO. Здесь они последний рубеж: сегодня
    -- значение вне границ молча прижималось к краю, и в отчёт ехала неправда.
    CONSTRAINT ck_analytics_event_payload CHECK (
        (player_count     IS NULL OR player_count     BETWEEN 0 AND 10)
        AND (team_count       IS NULL OR team_count       BETWEEN 0 AND 5)
        AND (word_count       IS NULL OR word_count       BETWEEN 0 AND 5000)
        AND (game_number      IS NULL OR game_number      BETWEEN 0 AND 10000)
        AND (duration_seconds IS NULL OR duration_seconds BETWEEN 0 AND 86400))
);

-- Статистика периода: findByCreatedAtBetweenOrderByCreatedAtAsc с пределом.
-- Второго индекса, по виду события, здесь нет: счётчики шести видов считаются
-- одним проходом по уже прочитанному отрезку (UsageStatisticsAssembler), и
-- запроса «события такого-то вида» не делает никто.
CREATE INDEX ix_analytics_event_period ON v2.analytics_event (occurred_at);


-- Переключатель возможности.
--
-- Ровно то же, что сегодня, плюс автор последней правки: флаг переключают
-- руками в базе, и «кто включил бота» — единственное, чего сейчас не узнать.
-- Списка допустимых имён в CHECK нет намеренно: набор закрыт проверкой на
-- входе (FeatureFlagName и public/features.js), и заводить новую возможность
-- через миграцию базы было бы дороже, чем она стоит.
CREATE TABLE v2.feature_flag (
    name        VARCHAR(60)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    description VARCHAR(300),
    updated_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_feature_flag PRIMARY KEY (name)
);


-- ═══════════════════════════ ops · admin ═══════════════════════════

-- Один снимок расхода.
--
-- Сегодня снимок — это строка usage_daily с ключом-датой и одним jsonb latest
-- внутри. Из этого следуют три беды сразу: снимок за день ровно один (а
-- собираются они четыре раза в сутки, 5:00, 11:00, 17:00 и 23:00 UTC — три из
-- четырёх затирают друг друга), по метрике нельзя ни отобрать, ни
-- отсортировать, и «кто снял» не записано вовсе.
--
-- Здесь остались только те поля снимка, которые не являются ни метрикой, ни
-- проверкой живости: они уехали в две таблицы ниже.
CREATE TABLE v2.usage_snapshot (
    id                     BIGINT      GENERATED ALWAYS AS IDENTITY,
    taken_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- schedule | admin | agent — по чьей воле снят. Сегодня неразличимо.
    taken_by               VARCHAR(16) NOT NULL DEFAULT 'schedule',
    -- Кто нажал «Проверить сейчас»; пусто у планового и агентского снимка.
    taken_by_player_id     UUID,
    -- День снимка в поясе квот. Месяц не хранится: он выводится отсюда, а
    -- generated-колонкой его не сделать — to_char по дате не IMMUTABLE.
    local_date             DATE        NOT NULL,
    quota_time_zone        VARCHAR(60) NOT NULL DEFAULT 'UTC',
    -- Метрики машины собираются только на Linux: на другой системе снимок
    -- берётся, но раздел VPS в нём пуст, и это надо отличать от нуля.
    host_metrics_available BOOLEAN     NOT NULL DEFAULT FALSE,
    host_metrics_error     VARCHAR(200),
    -- Три значения раздела VPS, которые не метрики: у них нет ни предела, ни
    -- периода, ни достоверности, и картой метрики они притворялись зря.
    load_average           NUMERIC(6, 2),
    cpu_cores              SMALLINT,
    livekit_sockets        INTEGER,
    turn_sockets           INTEGER,
    -- Настроен ли отправитель писем: mail.configured снимка.
    mail_configured        BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Что не удалось собрать. Сегодня это массив строк, но кладёт в него
    -- строку ровно один источник — пересчёт трафика VPS. Колонка, а не
    -- массив: второй источник получит своё имя в колонке, а список молча
    -- потерял бы, кто именно упал.
    collect_error          VARCHAR(400),
    CONSTRAINT pk_usage_snapshot PRIMARY KEY (id),
    CONSTRAINT ck_usage_snapshot_taken_by CHECK (taken_by IN ('schedule', 'admin', 'agent')),
    -- Ручной снимок обязан назвать администратора, плановый и агентский — нет.
    CONSTRAINT ck_usage_snapshot_actor CHECK ((taken_by = 'admin') = (taken_by_player_id IS NOT NULL)),
    CONSTRAINT ck_usage_snapshot_host
        CHECK (host_metrics_available OR load_average IS NULL)
);

-- Последний снимок: карточки админки показывают его.
CREATE INDEX ix_usage_snapshot_recent ON v2.usage_snapshot (taken_at DESC);
-- История по дням: findByDateBetweenOrderByDateDesc с пределом. Внутри дня
-- снимков теперь несколько, поэтому вторым сегментом время.
CREATE INDEX ix_usage_snapshot_history ON v2.usage_snapshot (local_date DESC, taken_at DESC);


-- Одна метрика снимка.
--
-- Двенадцать строк вместо двенадцати вложенных объектов jsonb:
-- database.reads / writes / deletes / storage, hosting.transfer / storage,
-- auth, vps.disk / memory / mediaStorage / networkTotal / networkMonthly,
-- mail.daily / monthly.
--
-- Выводимого здесь нет: remaining, percent и available (это ровно
-- used_value IS NOT NULL) считает DTO, как и сегодня.
--
-- А вот подпись, период, единица и достоверность ХРАНЯТСЯ, хотя выглядят
-- справочником. Снимок — исторический документ: подпись метрики базы менялась
-- вместе с самой базой («Firestore · чтения» → «PostgreSQL · размер базы»), и
-- перерисовывать позапрошлогодний снимок сегодняшним справочником значило бы
-- врать о том, что тогда измеряли.
CREATE TABLE v2.usage_metric_sample (
    snapshot_id  BIGINT       NOT NULL REFERENCES v2.usage_snapshot (id) ON DELETE CASCADE,
    -- Путь метрики в снимке: database.storage, vps.networkMonthly, mail.daily.
    metric_key   VARCHAR(60)  NOT NULL,
    label        VARCHAR(120) NOT NULL,
    -- Пусто — источник значения не дал; это не ноль.
    used_value   BIGINT,
    -- Пусто — предела нет или он не задан.
    limit_value  BIGINT,
    -- day | month | total | current
    period       VARCHAR(8)   NOT NULL,
    -- bytes либо штуки; в последнем случае единица названа в подписи.
    unit         VARCHAR(20)  NOT NULL,
    -- exact | estimate | lower_bound | tracked | tracked_estimate | unavailable
    accuracy     VARCHAR(20)  NOT NULL,
    -- Почему метрика такая, какая есть. Сегодня это два разных ключа, note и
    -- error, и читатель карточки не отличает их всё равно.
    note         VARCHAR(400),
    source       VARCHAR(60),
    CONSTRAINT pk_usage_metric_sample PRIMARY KEY (snapshot_id, metric_key),
    CONSTRAINT ck_usage_metric_sample_period CHECK (period IN ('day', 'month', 'total', 'current')),
    CONSTRAINT ck_usage_metric_sample_accuracy CHECK (accuracy IN (
        'exact', 'estimate', 'lower_bound', 'tracked', 'tracked_estimate', 'unavailable')),
    -- Достоверность и наличие значения не могут спорить.
    CONSTRAINT ck_usage_metric_sample_available
        CHECK ((accuracy = 'unavailable') = (used_value IS NULL)),
    CONSTRAINT ck_usage_metric_sample_values
        CHECK ((used_value IS NULL OR used_value >= 0) AND (limit_value IS NULL OR limit_value >= 0))
);
-- Своего индекса у метрик нет: их читают только вместе со снимком, то есть
-- по первому сегменту первичного ключа. Индекс (metric_key, snapshot_id DESC)
-- из §6.2 обслуживал бы историю ОДНОЙ метрики, а таблицу дней админка
-- строит из целых снимков и другого запроса не делает.


-- Проверки живости в момент снимка.
--
-- Десять строк вместо массива объектов внутри jsonb: сайт, наш API,
-- PostgreSQL, сама машина, четыре службы на ней (back, LiveKit, coturn,
-- nginx, HAProxy) и настроенность почты.
--
-- Булева ok рядом со status здесь нет: сегодня их два, и у предупреждения ok
-- равно true, а у неизвестного — null. Состояний ровно четыре, они названы.
CREATE TABLE v2.service_health_probe (
    snapshot_id BIGINT       NOT NULL REFERENCES v2.usage_snapshot (id) ON DELETE CASCADE,
    -- site | api | postgres | vps | back | livekit | turn | nginx | haproxy | email
    probe_key   VARCHAR(40)  NOT NULL,
    label       VARCHAR(60)  NOT NULL,
    -- ok | warn | down | unknown
    status      VARCHAR(8)   NOT NULL,
    detail      VARCHAR(300),
    -- Пусто у проверок, которые не ходят по сети.
    latency_ms  INTEGER,
    checked_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_service_health_probe PRIMARY KEY (snapshot_id, probe_key),
    CONSTRAINT ck_service_health_probe_status CHECK (status IN ('ok', 'warn', 'down', 'unknown')),
    CONSTRAINT ck_service_health_probe_latency CHECK (latency_ms IS NULL OR latency_ms >= 0)
);
-- Индексов нет по той же причине, что у метрик: проверки читаются только
-- вместе со своим снимком.


-- Последнее сырое показание счётчика ОС.
--
-- Заменяет usage_monitor_state. Месячный трафик считается ДЕЛЬТАМИ, потому
-- что счётчик /proc обнуляется при перезагрузке машины: строка нужна, чтобы
-- знать, от чего вычитать. @Version (§6.4) — сбор снимка это честный
-- read-modify-write, и два одновременных сбора (плановый и «проверить
-- сейчас») удвоили бы дельту.
CREATE TABLE v2.usage_traffic_meter (
    meter_id   VARCHAR(40) NOT NULL,
    tx_bytes   BIGINT,
    rx_bytes   BIGINT,
    read_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_usage_traffic_meter PRIMARY KEY (meter_id)
);


-- Накопленный трафик за период.
--
-- Заменяет usage_monthly и мёртвую usage_daily.vps_network_tx_bytes одной
-- таблицей с видом периода в ключе: дневной и месячный счётчики — одно
-- понятие с разной длиной шага, и держать под них две таблицы значило бы
-- дважды написать одно и то же прибавление.
--
-- tx_bytes — атомарный счётчик (§6.4).
CREATE TABLE v2.usage_traffic_period (
    meter_id    VARCHAR(40) NOT NULL REFERENCES v2.usage_traffic_meter (meter_id) ON DELETE CASCADE,
    -- day | month
    period_kind VARCHAR(8)  NOT NULL,
    -- 2026-09-06 либо 2026-09 — текстом, потому что шаг разный.
    period_key  VARCHAR(10) NOT NULL,
    tx_bytes    BIGINT      NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_usage_traffic_period PRIMARY KEY (meter_id, period_kind, period_key),
    CONSTRAINT ck_usage_traffic_period_kind CHECK (period_kind IN ('day', 'month')),
    CONSTRAINT ck_usage_traffic_period_bytes CHECK (tx_bytes >= 0)
);

-- История трафика: последние периоды сверху.
CREATE INDEX ix_usage_traffic_period_recent
    ON v2.usage_traffic_period (period_kind, period_key DESC);


-- Тревога о превышении порога.
--
-- Сегодня ключ склеен из трёх значений («2026-09_vps-диск_50»), причём
-- средним куском в него идёт РУССКАЯ ПОДПИСЬ метрики, приведённая к нижнему
-- регистру: переименование подписи заводит вторую тревогу о том же, и письмо
-- уходит второй раз. Здесь то же правило выражено уникальным индексом по
-- четырём колонкам, и подпись в него не входит.
CREATE TABLE v2.usage_alert (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY,
    -- day | month | total
    period_kind  VARCHAR(8)   NOT NULL,
    period_key   VARCHAR(10)  NOT NULL,
    metric_key   VARCHAR(60)  NOT NULL,
    metric_label VARCHAR(120) NOT NULL,
    threshold    SMALLINT     NOT NULL DEFAULT 50,
    -- Доля израсходованного на момент срабатывания.
    percent      NUMERIC(6, 2),
    -- pending | notified | failed — что стало с письмом.
    status       VARCHAR(16)  NOT NULL DEFAULT 'pending',
    -- Письмо, которым тревогу отправили; строка журнала писем ниже.
    email_id     BIGINT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    CONSTRAINT pk_usage_alert PRIMARY KEY (id),
    CONSTRAINT ux_usage_alert_period_metric UNIQUE (period_kind, period_key, metric_key, threshold),
    CONSTRAINT ck_usage_alert_period CHECK (period_kind IN ('day', 'month', 'total')),
    CONSTRAINT ck_usage_alert_status CHECK (status IN ('pending', 'notified', 'failed')),
    CONSTRAINT ck_usage_alert_threshold CHECK (threshold BETWEEN 1 AND 100)
);

-- Лента предупреждений в админке: findAllByOrderByCreatedAtDesc.
CREATE INDEX ix_usage_alert_recent ON v2.usage_alert (created_at DESC);


-- Ежедневный отчёт о расходе.
--
-- Сегодня в строке отчёта лежит jsonb-КОПИЯ всего снимка. Копия устаревает в
-- тот же миг, когда снимок пересобирают, и весит столько же, сколько сам
-- снимок. Здесь вместо копии ссылка.
CREATE TABLE v2.usage_report (
    report_date DATE        NOT NULL,
    -- Пусто, если отчёт готовили, а снимок снять не удалось.
    snapshot_id BIGINT      REFERENCES v2.usage_snapshot (id) ON DELETE SET NULL,
    email_id    BIGINT,
    time_zone   VARCHAR(60) NOT NULL DEFAULT 'UTC',
    -- pending | sent | failed
    status      VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT pk_usage_report PRIMARY KEY (report_date),
    CONSTRAINT ck_usage_report_status CHECK (status IN ('pending', 'sent', 'failed'))
);


-- Журнал исходящих писем.
--
-- Заменяет два jsonb-поля email (в тревоге и в отчёте) и таблицу-счётчик
-- usage_mail_counter сразу. Счётчик был выводимым: «сколько писем ушло за
-- сутки» — это число строк журнала за сутки, и держать рядом ещё и счётчик
-- значило дать им шанс разойтись. Ради этого счёта и стоит частичный индекс.
CREATE TABLE v2.outbound_email (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY,
    -- usage_alert | usage_report
    kind                VARCHAR(24)  NOT NULL,
    recipient           VARCHAR(320) NOT NULL,
    subject             VARCHAR(300) NOT NULL,
    -- queued | sent | failed
    state               VARCHAR(16)  NOT NULL DEFAULT 'queued',
    -- Идентификатор письма у отправителя; пусто, пока он не ответил.
    provider_message_id VARCHAR(120),
    failure_reason      VARCHAR(400),
    requested_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ,
    CONSTRAINT pk_outbound_email PRIMARY KEY (id),
    CONSTRAINT ck_outbound_email_kind CHECK (kind IN ('usage_alert', 'usage_report')),
    CONSTRAINT ck_outbound_email_state CHECK (state IN ('queued', 'sent', 'failed')),
    CONSTRAINT ck_outbound_email_sent CHECK ((state = 'sent') = (sent_at IS NOT NULL)),
    CONSTRAINT ck_outbound_email_failure CHECK ((state = 'failed') = (failure_reason IS NOT NULL))
);

-- Квота отправителя: сколько писем ушло за сутки и за месяц. Частичный —
-- в квоту идут только отправленные.
CREATE INDEX ix_outbound_email_sent ON v2.outbound_email (sent_at) WHERE state = 'sent';
-- Разбор «почему не пришёл вчерашний отчёт»: письма одного вида, свежие сверху.
CREATE INDEX ix_outbound_email_kind ON v2.outbound_email (kind, requested_at DESC);

-- Ссылки на письмо расставляются после того, как журнал заведён.
ALTER TABLE v2.usage_alert
    ADD CONSTRAINT fk_usage_alert_email
        FOREIGN KEY (email_id) REFERENCES v2.outbound_email (id) ON DELETE SET NULL;
ALTER TABLE v2.usage_report
    ADD CONSTRAINT fk_usage_report_email
        FOREIGN KEY (email_id) REFERENCES v2.outbound_email (id) ON DELETE SET NULL;


-- ═══════════════════════════ ops · machine ═══════════════════════════

-- Аренда на прогон уборки.
--
-- Заменяет maintenance_state, у которой была одна колонка last_run_at и одно
-- правило: «если прошлый прогон был меньше пяти минут назад — не начинать».
-- Правило читало значение, решало и писало обратно тремя отдельными шагами,
-- то есть два планировщика, запущенные одновременно, проходили его оба.
--
-- leased_until вместо last_run_at: аренда БЕРЁТСЯ на время, а не выводится из
-- отметки прошлого запуска. Разница видна, когда прогон падает посреди
-- работы: отметка «начал» осталась бы вечной, а аренда просто истекает.
-- @Version (§6.4) делает захват аренды одним условным обновлением.
CREATE TABLE v2.maintenance_lease (
    -- room_sweep | recording_sweep | token_sweep
    job          VARCHAR(40) NOT NULL,
    leased_until TIMESTAMPTZ,
    -- Кто держит аренду: имя узла или «cron». Без ключа, это не учётка.
    leased_by    VARCHAR(80),
    last_run_at  TIMESTAMPTZ,
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_maintenance_lease PRIMARY KEY (job),
    CONSTRAINT ck_maintenance_lease_job
        CHECK (job IN ('room_sweep', 'recording_sweep', 'token_sweep'))
);


-- История прогонов уборки.
--
-- Сегодня истории нет вовсе: у прохода по комнатам есть кулдаун и есть ответ
-- вызывающему, но узнать задним числом, когда уборка шла и что сделала,
-- негде. Два счётчика, а не один: сохранённые кем-то записи просматриваются,
-- но не удаляются, и «просмотрено» почти всегда больше «тронуто».
CREATE TABLE v2.maintenance_run (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY,
    job            VARCHAR(40) NOT NULL REFERENCES v2.maintenance_lease (job) ON DELETE CASCADE,
    -- cron | admin | agent
    triggered_by   VARCHAR(16) NOT NULL DEFAULT 'cron',
    -- running | succeeded | failed | skipped_cooldown
    state          VARCHAR(20) NOT NULL DEFAULT 'running',
    checked_count  INTEGER     NOT NULL DEFAULT 0,
    affected_count INTEGER     NOT NULL DEFAULT 0,
    failure_reason VARCHAR(400),
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    CONSTRAINT pk_maintenance_run PRIMARY KEY (id),
    CONSTRAINT ck_maintenance_run_trigger CHECK (triggered_by IN ('cron', 'admin', 'agent')),
    CONSTRAINT ck_maintenance_run_state
        CHECK (state IN ('running', 'succeeded', 'failed', 'skipped_cooldown')),
    CONSTRAINT ck_maintenance_run_counts CHECK (checked_count >= 0 AND affected_count >= 0),
    CONSTRAINT ck_maintenance_run_failure CHECK ((state = 'failed') = (failure_reason IS NOT NULL)),
    CONSTRAINT ck_maintenance_run_finished CHECK ((state = 'running') = (finished_at IS NULL))
);

-- «Когда последний раз убирали комнаты и чем кончилось».
CREATE INDEX ix_maintenance_run_job ON v2.maintenance_run (job, started_at DESC);
-- Разбор сбоев: только упавшие прогоны, свежие сверху.
CREATE INDEX ix_maintenance_run_failed
    ON v2.maintenance_run (started_at DESC) WHERE state = 'failed';


-- Что именно тронул прогон.
--
-- Ответ на «почему исчезла моя комната»: сегодня причина сноса приезжает
-- вызывающему в теле ответа (SweptRoomView.reason) и нигде не остаётся.
--
-- target_id — строка, а не ссылка, и это третий разряд колонок без ключа:
-- за одним столбцом стоят четыре разные сущности с тремя разными типами
-- ключа (комната — hat-…, запись — uuid, оба вида токенов — их собственные
-- ключи). Привести их к одному типу нечем, а четыре nullable-колонки со
-- взаимоисключающими ключами были бы хуже: три из них всегда пусты.
CREATE TABLE v2.maintenance_run_target (
    run_id      BIGINT       NOT NULL REFERENCES v2.maintenance_run (id) ON DELETE CASCADE,
    -- room | recording | refresh_token | password_reset_token
    target_kind VARCHAR(24)  NOT NULL,
    target_id   VARCHAR(180) NOT NULL,
    -- deleted | kept | failed
    outcome     VARCHAR(16)  NOT NULL,
    -- no-human-players | all-humans-left | stale-10m | abandoned-game |
    -- saved-by-player | expired — почему прогон решил именно так.
    reason      VARCHAR(60),
    at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_maintenance_run_target PRIMARY KEY (run_id, target_kind, target_id),
    CONSTRAINT ck_maintenance_run_target_kind
        CHECK (target_kind IN ('room', 'recording', 'refresh_token', 'password_reset_token')),
    CONSTRAINT ck_maintenance_run_target_outcome CHECK (outcome IN ('deleted', 'kept', 'failed'))
);

-- «Что случилось с этой комнатой»: история одного объекта, свежее сверху.
CREATE INDEX ix_maintenance_run_target_object
    ON v2.maintenance_run_target (target_kind, target_id, run_id DESC);


-- ═══════════════════════════ ops · тест-боты ═══════════════════════════
-- Третий адрес из разбора семнадцати колонок про тест-ботов, обещанный
-- хвостом V15: признак «это бот» остался в кластере комнаты, состояние бота
-- как игрока — в тех же строках, что у человека, а прогон, его владелец и его
-- часы живут здесь. Из одиннадцати ключей jsonb test_bot_runtime сюда доехал
-- ровно один — testBotLastShotAt.

-- Прогон тестовых ботов в комнате.
CREATE TABLE v2.test_bot_run (
    id                        BIGINT      GENERATED ALWAYS AS IDENTITY,
    room_id                   VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    -- Владелец сервиса, запустивший прогон. Сегодня это room.test_owner_uid,
    -- и при пустом значении право молча падало обратно на created_by комнаты
    -- — то есть на колонку, которую переписывает передача хозяйства.
    owner_player_id           UUID        NOT NULL,
    -- running | stopped
    state                     VARCHAR(16) NOT NULL DEFAULT 'running',
    bot_count                 SMALLINT    NOT NULL DEFAULT 0,
    -- В какой партии и на каком её ходу объяснял сам владелец. Пара нужна
    -- сценарию прогона: первые два хода владельца ведёт он, дальше боты.
    owner_explainer_game_number INTEGER   NOT NULL DEFAULT 0,
    owner_explainer_turn_number INTEGER   NOT NULL DEFAULT 0,
    started_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    stopped_at                TIMESTAMPTZ,
    CONSTRAINT pk_test_bot_run PRIMARY KEY (id),
    CONSTRAINT ck_test_bot_run_state CHECK (state IN ('running', 'stopped')),
    CONSTRAINT ck_test_bot_run_bot_count CHECK (bot_count BETWEEN 0 AND 9),
    CONSTRAINT ck_test_bot_run_stopped CHECK ((state = 'stopped') = (stopped_at IS NOT NULL))
);

-- Один идущий прогон на комнату: два запуска подряд заводили вторую обойму
-- ботов поверх первой, и остановка гасила только последнюю.
CREATE UNIQUE INDEX ux_test_bot_run_room
    ON v2.test_bot_run (room_id) WHERE state = 'running';
-- «Мои прогоны», свежие сверху.
CREATE INDEX ix_test_bot_run_owner ON v2.test_bot_run (owner_player_id, started_at DESC);


-- Один бот прогона.
--
-- bot_player_id — синтетический uuid, и это ради него: со своим uuid строки
-- бота ничем не отличаются от строк человека, поэтому правила партии не
-- приходится писать дважды. Сегодня идентификатор бота выглядит как
-- testbot-<комната>-<номер>, и ключа на учётку у него нет и не будет.
--
-- display_name живёт здесь, а не в справочнике игроков: у бота нет карточки,
-- а ProfileDirectoryPort отвечает только за учётки. Комнате имя отдаёт порт
-- этой области.
--
-- fillfactor 80: last_shot_at переписывается на каждом выстреле бота.
-- Индекса по этой колонке намеренно нет (§6.2 называет
-- «(run_id, last_shot_at NULLS FIRST)»): у прогона не больше девяти ботов,
-- отбор «кто дольше всех не стрелял» идёт по префиксу первичного ключа и
-- сортирует девять строк, а вот индекс по last_shot_at запретил бы
-- HOT-обновление отметки — то есть стоил бы дороже, чем экономит.
CREATE TABLE v2.test_bot (
    run_id        BIGINT      NOT NULL REFERENCES v2.test_bot_run (id) ON DELETE CASCADE,
    bot_player_id UUID        NOT NULL,
    slot_index    SMALLINT    NOT NULL,
    display_name  VARCHAR(20) NOT NULL,
    last_shot_at  TIMESTAMPTZ,
    CONSTRAINT pk_test_bot PRIMARY KEY (run_id, bot_player_id),
    CONSTRAINT ux_test_bot_slot UNIQUE (run_id, slot_index),
    CONSTRAINT ck_test_bot_slot CHECK (slot_index BETWEEN 1 AND 9)
) WITH (fillfactor = 80);


-- Часы прогона ботов.
--
-- Три колонки next_*_at жили в строке комнаты на 103 колонки, и тик прогона —
-- он идёт примерно раз в секунду — переписывал вместе с ними настройки,
-- составы, приглашения и мешок слов. Здесь их пять, и fillfactor 70 оставляет
-- в странице место под новые версии строки: обновление проходит внутри
-- страницы (HOT-update) и не трогает индексы — тот же приём, что у
-- v2.player_presence в V13 и v2.room_presence в V15.
--
-- Индекса по next_action_at нет (§6.2 называет «(next_action_at)»): тик
-- сегодня запускает КЛИЕНТ, обращаясь к своей комнате, то есть строка
-- читается по первичному ключу. Серверного планировщика, которому нужен был
-- бы список «кому пора ходить», не существует — а появись он, индекс по
-- вечно меняющейся колонке отменил бы HOT-обновление, ради которого таблица
-- и отделена.
--
-- special_effect_* — одна пара вместо двух: сегодня рядом лежит
-- test_bot_voice_effect_* с тем же смыслом, и обновляется она, как прямо
-- написано в коде, «ради совместимости с прежними клиентами» (§3.2, §6.3).
CREATE TABLE v2.test_bot_schedule (
    run_id                 BIGINT      NOT NULL REFERENCES v2.test_bot_run (id) ON DELETE CASCADE,
    next_action_at         TIMESTAMPTZ,
    next_sabotage_at       TIMESTAMPTZ,
    next_chat_at           TIMESTAMPTZ,
    special_effect_until   TIMESTAMPTZ,
    special_effect_cursor  INTEGER     NOT NULL DEFAULT 0,
    version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_test_bot_schedule PRIMARY KEY (run_id),
    CONSTRAINT ck_test_bot_schedule_cursor CHECK (special_effect_cursor >= 0)
) WITH (fillfactor = 70);


-- ═══════════════ Четыре ключа, отложенные V11 и V15: почему их всё ещё нет ═══════════════
--
-- V15 написала прямо: «meme_id (media) и recording (recording) — таблиц
-- v2.meme и v2.recording не существует. Ключи появятся вместе с ними».
-- Таблицы появились, а ключи — нет, и это не забывчивость. Проверено запуском:
-- ALTER TABLE ... ADD CONSTRAINT падает здесь и сейчас.
--
--   v2.player_default_loadout.meme_id → v2.meme (id)
--       Таблица НЕ пуста: V16 перенесла в неё 560 обойм, а идентификаторы
--       мемов в них выведены мостом — md5('meme:' || <старый id>)::uuid.
--       В v2.meme этих строк нет и быть не может: каталог не переехал.
--       Хуже того, backfill из meme_library не спас бы — из тридцати девяти
--       мемов, встречающихся в обоймах, в meme_library лежат восемь.
--       Остальные тридцать один — встроенные, они строкой в таблице не
--       хранятся вовсе.
--
--   v2.match_loadout_slot.meme_id  → v2.meme (id)
--   v2.sabotage_event.meme_id      → v2.meme (id)
--       Сегодня обе пусты, и ключ добавился бы. Но добавить его сейчас значит
--       поставить мину под переезд партии: партия переедет раньше каталога —
--       и первый же выстрел мемом упадёт на несуществующей строке v2.meme.
--
--   v2.direct_chat_message.shared_recording_id → v2.recording (id)
--       Переписка УЖЕ на v2 и уже пишет сюда: ShareRecordingInChatUseCase
--       кладёт md5('recording:' || <старый id>)::uuid, а v2.recording пуста.
--       Ключ с RESTRICT — а другого здесь нельзя, ck_direct_chat_message_payload
--       требует у сообщения вида recording непустой ссылки — сломал бы отправку
--       записи в чат в тот же день, когда выкатится.
--
-- Отсюда правило, которое стоит записать: ВНЕШНИЙ КЛЮЧ НАРУЖУ КЛАСТЕРА
-- СТАВИТСЯ НЕ ВМЕСТЕ С ТАБЛИЦЕЙ, А ВМЕСТЕ С ПЕРЕЕЗДОМ ОБЛАСТИ-ВЛАДЕЛЬЦА.
-- Пока область не переехала, её строки живут в public, а в v2 от них остаются
-- выведенные мостом uuid, за которыми ничего нет. Это и есть цена моста
-- v2.legacy_id_bridge — она заплачена сознательно (V12), и оплачивать её
-- вторично падением на ограничении не нужно.
--
-- Что должно быть в миграции переключения media (она же снимает мост
-- kind='meme', как обещала V16):
--     INSERT INTO v2.meme ... FROM meme_library + посев встроенных;
--     ALTER TABLE v2.player_default_loadout ADD CONSTRAINT
--         fk_player_default_loadout_meme FOREIGN KEY (meme_id)
--         REFERENCES v2.meme (id) ON DELETE RESTRICT;
--     то же для v2.match_loadout_slot;
--     для v2.sabotage_event — ON DELETE SET NULL: журнал выстрелов обязан
--     пережить мем, и колонка там и так пуста у оружия, бьющего по сцене.
-- Что должно быть в миграции переключения recording:
--     INSERT INTO v2.recording ... FROM game_recording (мост kind='recording');
--     ALTER TABLE v2.direct_chat_message ADD CONSTRAINT
--         fk_direct_chat_message_recording FOREIGN KEY (shared_recording_id)
--         REFERENCES v2.recording (id) ON DELETE RESTRICT.
-- RESTRICT, а не CASCADE: истёкшая запись теряет ФАЙЛ
-- (recording_artifact.deleted_at), а паспорт остаётся — иначе ссылка в
-- переписке указывала бы в пустоту.


-- ═══════════════ Что стало с jsonb этих трёх кластеров ═══════════════
--
--   game_recording.participants      → v2.recording_participant
--   game_recording.participant_uids  → они же (вторая копия тех же uid)
--   game_recording.teams             → v2.recording_team
--   game_recording.winner_team_ids   → v2.recording_team.is_winner
--   game_recording.winner_team_names → выводится из recording_team.name
--   game_recording.saved_by          → v2.recording_save
--   game_recording.shared_with       → v2.recording_share
--   analytics_event.payload          → шесть типизированных колонок
--   usage_daily.latest               → v2.usage_snapshot + usage_metric_sample
--                                      + service_health_probe
--   usage_alert.metric               → колонки metric_key/metric_label/percent
--   usage_alert.email                → v2.outbound_email
--   usage_report.snapshot            → ссылка на v2.usage_snapshot
--   usage_report.email               → v2.outbound_email
--   room.test_bot_ids                → v2.test_bot
--   room.test_bot_runtime            → строки диверсий (V15) + v2.test_bot
--
-- Осталось jsonb: один, recording_egress_event.payload. Он объявлен в §6 как
-- один из двух на всю базу, и причина у него та же, что у
-- moderation_action.details: форму задаёт не наш код. Тело вебхука LiveKit —
-- чужой контракт, и колонки под него означали бы обещание, что он не
-- пополнится. Четыре значения, которые мы из него ЧИТАЕМ, лежат рядом
-- колонками; payload остаётся ради разбора инцидентов.
--
--
-- ═══════════════ Что выброшено из сегодняшних таблиц ═══════════════
--
-- ВЫВОДИМОЕ. game_recording.winning_score, total_score, saved_count и
-- shared_count — считаются по строкам recording_team, recording_save и
-- recording_share; usage_mail_counter целиком — это число строк журнала писем
-- за период; meme_library.src и poster (адреса) — строятся из storage_key;
-- recording.room_name дублировался ещё и в title.
--
-- МЁРТВОЕ (пишется, не читается). game_recording.secret_word_recorded,
-- recording_view, start_lock_at_ms, last_stop_attempt_at_ms;
-- usage_daily.vps_network_tx_bytes; room.test_bot_voice_effect_until и
-- test_bot_voice_effect_cursor.
--
-- КОПИИ ЧУЖИХ ДАННЫХ. meme_library.owner_name (имя владельца читается через
-- ProfileDirectoryPort), usage_report.snapshot (копия всего снимка внутри
-- отчёта), analytics_event.email (почта читается по player_id).
--
-- ДВА ПРЕДСТАВЛЕНИЯ ВРЕМЕНИ. Все *_ms bigint game_recording — started_at_ms,
-- finished_at_ms, expires_at_ms, deleted_at_ms, egress_active_at_ms,
-- egress_ended_at_ms, recorder_ready_at_ms, recorder_start_signal_at_ms,
-- last_egress_sync_at_ms — и maintenance_state.last_run_at. Остался
-- timestamptz, миллисекунды считает DTO.
--
-- ФЛАГИ, СЛИТЫЕ В ПЕРЕЧИСЛЕНИЯ. meme_library.builtin + recovered_from_s3 →
-- meme.origin; game_recording.status + livekit_status →
-- recording_egress_job.state (+ recording_artifact.deleted_at).
--
-- НЕ ПЕРЕЕЗЖАЕТ ВОВСЕ. meme_library.data_url — байты ролика прямо в строке
-- карточки, до нескольких мегабайт: чтение библиотеки тянуло их в память на
-- каждую карточку. В v2 ролик живёт только в хранилище; что делать с
-- легаси-строками, решает переключение области — либо выгрузить их в бакет,
-- либо оставить в старой таблице. Вместе с ним не переезжают
-- storage_provider, media_version и byte_size: первое всегда одно и то же,
-- второе стало meme_asset.revision, третье — meme_asset.size_bytes.
--
--
-- ═══════════════ Чем этот файл отличается от списка индексов §6.2 ═══════════════
-- Правило разреза: индекс заводится под запрос, который делает сегодняшний
-- код, а не под запрос, который когда-нибудь может понадобиться. Восемь
-- индексов из §6.2 под это правило не подошли, и каждый объяснён на месте:
--
--   usage_metric_sample (metric_key, snapshot_id DESC) — историю ОДНОЙ
--       метрики никто не спрашивает: админка строит таблицу дней из целых
--       снимков, а они читаются префиксом первичного ключа;
--   service_health_probe (probe_key, snapshot_id DESC) и
--       (snapshot_id) WHERE status <> 'ok' — то же самое;
--   analytics_event (event_type, occurred_at) — отбора по виду события нет:
--       счётчики шести видов считаются одним проходом по отрезку;
--   recording_artifact (recording_id) WHERE не удалён — артефакт читается по
--       своей записи, то есть по первичному ключу; выборки «все живые файлы»
--       не делает ни уборка (она идёт от срока хранения), ни админка;
--   room_recording_policy (room_id) WHERE enabled — флаг читается по своей
--       комнате;
--   test_bot (run_id, last_shot_at NULLS FIRST) и
--       test_bot_schedule (next_action_at) — оба отменили бы HOT-обновление
--       самой горячей колонки своей таблицы, а обслуживали бы либо девять
--       строк, либо серверный планировщик, которого нет.
--
-- Два индекса уточнены, а не выброшены. §6.2 называет у мема
-- «(division, status, created_at DESC)» — здесь это ЧАСТИЧНЫЙ индекс по
-- (division_language, created_at DESC) с условием status = 'active': статус в
-- запросе всегда один и тот же, и держать его колонкой значит хранить в
-- индексе черновики и снятые мемы, которые витрина не покажет никогда.
-- У журнала писем §6.2 называет «(requested_at) WHERE sent» — здесь ключ
-- индекса sent_at: квота отправителя считается по времени ОТПРАВКИ, а между
-- постановкой в очередь и отправкой лежит сетевой вызов, и на границе суток
-- эти два времени расходятся.
