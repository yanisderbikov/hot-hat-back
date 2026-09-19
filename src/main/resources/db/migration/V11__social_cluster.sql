-- Кластер социального по §6 плана v2: дружба, переписка, рейтинговые команды,
-- таблицы сезона. Семнадцать таблиц вместо сегодняшних тринадцати и ни одной
-- колонки jsonb вместо двадцати (см. разбор в конце файла).
--
-- ─────────────────── Почему схема v2, а не public ───────────────────
-- Старые таблицы остаются работать: на них живёт фронт и адреса /api/portal.
-- Пять целевых имён заняты сегодняшними таблицами — friend_request,
-- direct_chat, direct_chat_message, ranked_team, team_invite, — поэтому новый
-- кластер кладётся в отдельную схему. Альтернатива (суффикс _v2 в имени
-- таблицы) испортила бы имена навсегда: суффикс пришлось бы либо тащить в
-- продакшн, либо переименовывать семнадцать таблиц вместе со всеми ссылками.
-- Схема снимается одним ALTER TABLE ... SET SCHEMA в день, когда старые
-- таблицы уйдут, а до того полностью изолирует два поколения схемы друг от
-- друга: ошибиться таблицей нельзя даже опечаткой.
--
-- ─────────────────── Почему player_id uuid ───────────────────
-- §6.1 плана разрешает расхождение «uid varchar(160) против player_id uuid» в
-- пользу uuid: фактические значения сегодня — 28 символов base62, длина 160 не
-- значила ничего. База пересобирается полностью (решение заказчика №3),
-- переносить нечего. Ловушка здесь одна и она отмечена в отчёте: сегодняшний
-- AuthServiceImpl.newUid() выдаёт base62, и до переезда области auth ни одна
-- из этих таблиц не может быть заполнена настоящими значениями.
--
-- ─────────────────── Почему нет внешних ключей на игрока и комнату ───────────────────
-- Таблица user_account (владелец — auth) и новая room (владелец — room) ещё не
-- существуют, а ссылаться на старые app_user.uid и room.id нельзя: там другой
-- тип ключа и другой владелец. Внутри кластера внешние ключи расставлены все.
--
-- Данные не переносятся: старые таблицы остаются как есть, новые пусты.

CREATE SCHEMA IF NOT EXISTS v2;


-- ═══════════════════════════ friends ═══════════════════════════

-- Подтверждённая дружба: одна строка на пару.
--
-- Сегодня ключ — friend_link.pair, склейка двух uid в VARCHAR(340), а рядом
-- лежат те же uid отдельными колонками и третьей копией — в jsonb member_uids.
-- Три представления одного факта. Здесь ключ — сама пара, а порядок в ней
-- задан ограничением: «Ваня и Петя» и «Петя и Ваня» физически не могут стать
-- двумя строками, и склеивать строку, чтобы это проверить, больше не нужно.
CREATE TABLE v2.friendship (
    player_low  UUID        NOT NULL,
    player_high UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_friendship PRIMARY KEY (player_low, player_high),
    -- Строгое неравенство заодно запрещает дружбу с самим собой.
    CONSTRAINT ck_friendship_order CHECK (player_low < player_high)
);

-- «Друзья игрока» — это findByUidAOrUidB (SocialManager.getLinksOf): два
-- поиска, по каждой стороне пары. Первый закрыт первичным ключом, второму
-- нужен свой индекс. INCLUDE (created_at) кладёт в него и дату: список друзей
-- показывает её сразу, и без INCLUDE каждая сотая строка стоила бы похода
-- в таблицу.
CREATE INDEX ix_friendship_high ON v2.friendship (player_high) INCLUDE (created_at);


-- Заявка в друзья. Свой идентификатор остаётся: им отвечают на заявку
-- (POST /friends/requests/{id}/accept), и он уже в спецификации как число.
--
-- Направление (кто позвал, кого позвали) и пара (между кем заявка) — разные
-- вещи, и раньше они хранились двумя способами сразу: from_uid/to_uid плюс
-- склеенный pair. Здесь направление — две колонки, пара — две вычисляемые:
-- их считает база, разойтись с направлением они не могут.
CREATE TABLE v2.friend_request (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requester   UUID        NOT NULL,
    addressee   UUID        NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at TIMESTAMPTZ,
    player_low  UUID GENERATED ALWAYS AS (LEAST(requester, addressee))    STORED,
    player_high UUID GENERATED ALWAYS AS (GREATEST(requester, addressee)) STORED,
    CONSTRAINT ck_friend_request_status   CHECK (status IN ('pending', 'accepted', 'declined')),
    CONSTRAINT ck_friend_request_self     CHECK (requester <> addressee),
    -- Ответ и отметка времени ответа появляются вместе: «принята, но когда —
    -- неизвестно» показывать нечем, у ответа в контракте есть answeredAtMs.
    CONSTRAINT ck_friend_request_answered CHECK ((status = 'pending') = (answered_at IS NULL))
);

-- Одна живая заявка на пару в любую сторону. Сегодня это чтение перед
-- вставкой (getPendingByPair), то есть check-then-act: два встречных
-- приглашения, отправленных одновременно, создают две заявки, и приняв обе,
-- игроки получают дружбу дважды. Уникальный индекс закрывает гонку.
CREATE UNIQUE INDEX ux_friend_request_pending
    ON v2.friend_request (player_low, player_high) WHERE status = 'pending';

-- Входящие: экран заявок читает только ждущие ответа (getIncomingRequests
-- фильтрует по status='pending'), поэтому индекс частичный.
CREATE INDEX ix_friend_request_inbox
    ON v2.friend_request (addressee, created_at DESC) WHERE status = 'pending';

-- Исходящие: ListOutgoingFriendRequestsUseCase берёт и ждущие, и принятые
-- (по принятым горит значок в шапке) и пропускает только отклонённые.
-- Поэтому предикат здесь не 'pending', как у входящих, а «не отклонена» —
-- иначе половина списка искалась бы мимо индекса.
CREATE INDEX ix_friend_request_outbox
    ON v2.friend_request (requester, created_at DESC) WHERE status <> 'declined';


-- ═══════════════════════════ chat ═══════════════════════════

-- Шапка переписки: пара и указатель на последнее сообщение.
--
-- Ключ — суррогат, а не склейка uid: pair VARCHAR(340) ездил внешним ключом
-- в каждое сообщение, и сорок лишних байт на строку сообщения оплачивались
-- только тем, что ключ читаем глазами.
--
-- last_message_id и last_message_at выводимы из сообщений, и держатся здесь
-- ровно ради одного запроса: список переписок сортируется по свежести и
-- показывает превью. Без указателя это «последнее в каждой группе» —
-- скоррелированный подзапрос findLastOfEachPair, который сегодня и написан
-- руками. Текст превью и автор при этом НЕ копируются: их отдаёт строка
-- сообщения, на которую указывает last_message_id.
CREATE TABLE v2.direct_chat (
    chat_id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_low      UUID        NOT NULL,
    player_high     UUID        NOT NULL,
    last_message_id BIGINT,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_direct_chat_pair  UNIQUE (player_low, player_high),
    CONSTRAINT ck_direct_chat_order CHECK (player_low < player_high)
);

-- Одно сообщение переписки. Идентификатор — он же курсор истории: база
-- выдаёт его строго возрастающим, а отметка времени у двух сообщений может
-- совпасть до миллисекунды (ровно это объясняет комментарий у сегодняшнего
-- findLastOfEachPair).
--
-- Вид сообщения называет сервер, а не восстанавливает клиент, заглядывая в
-- attachment.kind: сегодня это ветвление повторено в трёх местах фронта.
-- Получатель не хранится: в переписке двое, второй — это тот из пары, кто не
-- отправитель.
CREATE TABLE v2.direct_chat_message (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id             BIGINT      NOT NULL REFERENCES v2.direct_chat (chat_id) ON DELETE CASCADE,
    sender_player_id    UUID        NOT NULL,
    kind                VARCHAR(16) NOT NULL,
    -- 800 символов — предел, которым сегодня режет текст SocialServiceImpl.
    body                VARCHAR(800),
    -- Ссылки на чужие области: записи и приглашения в комнату. Внешних ключей
    -- нет, пока нет таблиц recording и room_invite.
    shared_recording_id UUID,
    room_invite_id      UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_direct_chat_message_kind CHECK (kind IN ('text', 'image', 'recording', 'room_invite')),
    -- Вид и его наполнение не могут разойтись: у записи есть запись,
    -- у приглашения — приглашение, у текста есть текст. Картинка лежит
    -- отдельной строкой и может идти с подписью, поэтому про body у неё
    -- условия нет.
    CONSTRAINT ck_direct_chat_message_payload CHECK (
        (kind = 'text'        AND body IS NOT NULL     AND shared_recording_id IS NULL AND room_invite_id IS NULL)
     OR (kind = 'image'                                AND shared_recording_id IS NULL AND room_invite_id IS NULL)
     OR (kind = 'recording'   AND shared_recording_id IS NOT NULL AND room_invite_id IS NULL)
     OR (kind = 'room_invite' AND room_invite_id IS NOT NULL      AND shared_recording_id IS NULL))
);

-- История переписки листается назад от свежего: findByPairOrderByCreatedAtDesc
-- с пределом. Сортировка по id, а не по времени, — по той же причине, по
-- которой id стал курсором.
CREATE INDEX ix_direct_chat_message_history ON v2.direct_chat_message (chat_id, id DESC);

-- Приглашения в комнату ищут своё сообщение, чтобы обновить его состояние.
-- Частичный: у подавляющего большинства сообщений колонка пуста.
CREATE INDEX ix_direct_chat_message_invite
    ON v2.direct_chat_message (room_invite_id) WHERE room_invite_id IS NOT NULL;

-- Внешний ключ шапки на сообщение добавляется после таблицы сообщений:
-- ссылка круговая, и разорвать её может только порядок создания. Индекс под
-- ним нужен не чтениям, а удалению: без него каждое удаление сообщения
-- сканировало бы все шапки целиком.
ALTER TABLE v2.direct_chat
    ADD CONSTRAINT fk_direct_chat_last_message
    FOREIGN KEY (last_message_id) REFERENCES v2.direct_chat_message (id) ON DELETE SET NULL;
CREATE INDEX ix_direct_chat_last_message ON v2.direct_chat (last_message_id);

-- Непрочитанное и докуда дочитано — на участника, а не на переписку.
--
-- Сегодня это три jsonb в шапке: unread_counts, unread_for и last_read_at_ms,
-- по ключу-uid в каждом. Оба участника пишут один и тот же документ, читая
-- его целиком, — и «отправил» одного затирает «прочитал» другого. Строка на
-- участника делает счётчик атомарным: UPDATE ... SET unread_count =
-- unread_count + 1 не читает значение в память вовсе.
CREATE TABLE v2.direct_chat_member (
    chat_id              BIGINT      NOT NULL REFERENCES v2.direct_chat (chat_id) ON DELETE CASCADE,
    player_id            UUID        NOT NULL,
    unread_count         INTEGER     NOT NULL DEFAULT 0,
    -- Отметка «дочитано досюда». Внешнего ключа нет намеренно: это водяной
    -- знак, и он обязан пережить удаление сообщения, на которое показывает,
    -- иначе удаление одного сообщения обнулит прогресс чтения.
    last_read_message_id BIGINT,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_direct_chat_member     PRIMARY KEY (chat_id, player_id),
    CONSTRAINT ck_direct_chat_member_unread CHECK (unread_count >= 0)
);

-- «Мои переписки»: сегодня список берётся обходом связей дружбы и чтением
-- шапки на каждую пару. По своим строкам он читается одной выборкой.
CREATE INDEX ix_direct_chat_member_player ON v2.direct_chat_member (player_id, chat_id);

-- Значок непрочитанного в шапке портала спрашивает только «есть ли хоть
-- что-то». Частичный индекс — это ровно те строки, где есть.
CREATE INDEX ix_direct_chat_member_unread
    ON v2.direct_chat_member (player_id) WHERE unread_count > 0;

-- Картинка сообщения отдельной строкой, а не полем в сообщении.
--
-- data-URL до 120 000 символов (предел SocialServiceImpl) в строке сообщения
-- означал бы, что любое чтение истории тащит из TOAST все картинки страницы,
-- даже когда рисуется список превью. Разделение оставляет строку сообщения
-- маленькой, а картинку берут отдельно и только когда показывают.
CREATE TABLE v2.direct_chat_photo (
    message_id BIGINT         PRIMARY KEY REFERENCES v2.direct_chat_message (id) ON DELETE CASCADE,
    data_url   VARCHAR(120000) NOT NULL,
    width      INTEGER        NOT NULL DEFAULT 0,
    height     INTEGER        NOT NULL DEFAULT 0,
    file_name  VARCHAR(80),
    -- Те же границы, которыми сегодня зажимает размеры cleanAttachment.
    CONSTRAINT ck_direct_chat_photo_size
        CHECK (width BETWEEN 0 AND 3000 AND height BETWEEN 0 AND 3000)
);


-- ═══════════════════════════ team ═══════════════════════════

-- Постоянная пара игроков одного дивизиона.
--
-- Состав уехал в ranked_team_member: сегодня он лежит здесь двумя jsonb
-- (member_uids и member_nicknames) плюс owner_uid, плюс третьей копией — в
-- app_user.ranked_team_id/ranked_team_status. Ники — чужие данные, они
-- приходят из карточки игрока и здесь не хранятся вовсе.
--
-- Колонки rating тут нет: сегодняшний jsonb {classic:1000, sabotage:1000}
-- присваивается при основании и не меняется никогда (§6.3, «вечная тысяча»).
CREATE TABLE v2.ranked_team (
    id                UUID        PRIMARY KEY,
    -- Ids.TEAM_NAME допускает 3–30 символов.
    name              VARCHAR(30) NOT NULL,
    -- Ключ уникальности вместо отдельной таблицы ranked_team_name. Выражение
    -- повторяет Ids.key(): trim + lower. Считает база, поэтому «занято ли имя»
    -- и «под каким ключом лежит имя» не могут разойтись; вместе с таблицей
    -- исчезает и её ручная синхронизация при роспуске команды.
    name_key          VARCHAR(30) GENERATED ALWAYS AS (lower(btrim(name))) STORED,
    -- Проверять по списку дивизионов нечем: девять кодов живут в Divisions и
    -- в файлах public/i18n, и CHECK стал бы их третьей копией, расходящейся
    -- при добавлении языка.
    division_language VARCHAR(8)  NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at      TIMESTAMPTZ,
    -- Оптимистическая блокировка (§6.4). Сегодня согласованность команды
    -- держится сравнением updatedAt в DocumentServiceImpl — это check-then-act
    -- (находка B2 аудита): между чтением и записью помещается чужая правка.
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_ranked_team_status    CHECK (status IN ('pending', 'active')),
    -- Команда становится активной ровно в момент подтверждения напарником.
    CONSTRAINT ck_ranked_team_confirmed CHECK ((status = 'active') = (confirmed_at IS NOT NULL))
);

-- Уникальность названия. Сегодня это чтение перед вставкой (getTeamName), то
-- есть 500 на гонке двух одинаковых названий.
CREATE UNIQUE INDEX ux_ranked_team_name_key ON v2.ranked_team (name_key);

-- Логотип до 280 КБ отдельной строкой — по той же причине, что и фото чата:
-- карточка команды читается на каждом экране рейтинга, логотип нужен не всем
-- из них, а строка с ним весит в тысячу раз больше остальных колонок.
CREATE TABLE v2.ranked_team_logo (
    team_id    UUID           PRIMARY KEY REFERENCES v2.ranked_team (id) ON DELETE CASCADE,
    data_url   VARCHAR(280000) NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Состав команды: строка на участника.
CREATE TABLE v2.ranked_team_member (
    team_id   UUID        NOT NULL REFERENCES v2.ranked_team (id) ON DELETE CASCADE,
    player_id UUID        NOT NULL,
    role      VARCHAR(16) NOT NULL,
    status    VARCHAR(16) NOT NULL DEFAULT 'pending',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_ranked_team_member     PRIMARY KEY (team_id, player_id),
    CONSTRAINT ck_ranked_team_member_role   CHECK (role IN ('captain', 'partner')),
    CONSTRAINT ck_ranked_team_member_status CHECK (status IN ('pending', 'active'))
);

-- Одна команда на игрока — в любом состоянии, а не только в активном.
-- Так ведёт себя код: app_user.ranked_team_id однозначен, и ALREADY_IN_TEAM
-- срабатывает у основателя ещё до ответа напарника. Строка напарника
-- появляется только при принятии приглашения, поэтому несколько ждущих
-- приглашений этот индекс не запрещает — их запрещает ux_team_invite_open
-- со стороны команды.
CREATE UNIQUE INDEX ux_ranked_team_member_player ON v2.ranked_team_member (player_id);

-- Капитан у команды один. Без оговорки про состояние: строка капитана
-- заводится вместе с командой и другой роли не принимает.
CREATE UNIQUE INDEX ux_ranked_team_member_captain
    ON v2.ranked_team_member (team_id) WHERE role = 'captain';

-- Приглашение напарника в команду.
--
-- Копии чужих данных (team_name, owner_nickname, division_language) сюда не
-- переехали: их отдаёт команда и карточка игрока.
CREATE TABLE v2.team_invite (
    id                UUID        PRIMARY KEY,
    team_id           UUID        NOT NULL REFERENCES v2.ranked_team (id) ON DELETE CASCADE,
    invitee_player_id UUID        NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at       TIMESTAMPTZ,
    -- Срок из §6.2. Сегодня приглашение в команду не истекает вовсе, поэтому
    -- колонка допускает пустоту: пока правило не заведено, писать в неё нечего.
    expires_at        TIMESTAMPTZ,
    CONSTRAINT ck_team_invite_status   CHECK (status IN ('pending', 'accepted', 'declined')),
    CONSTRAINT ck_team_invite_answered CHECK ((status = 'pending') = (answered_at IS NULL))
);

-- Входящие приглашения игрока: getPendingInvites фильтрует по состоянию.
CREATE INDEX ix_team_invite_inbox
    ON v2.team_invite (invitee_player_id, created_at DESC) WHERE status = 'pending';

-- Сторожу, который будет гасить просроченные, нужны только ждущие ответа.
CREATE INDEX ix_team_invite_expiry
    ON v2.team_invite (expires_at) WHERE status = 'pending';

-- У команды одно живое приглашение: она заводится вместе с ним и второго
-- напарника у пары быть не может.
CREATE UNIQUE INDEX ux_team_invite_open ON v2.team_invite (team_id) WHERE status = 'pending';

-- Сессия предматчевой проверки: одна на команду.
--
-- session_seq — почему он здесь: новый префлайт полностью заменяет старый и
-- флаги готовности не переносятся (так и написано в startPreflight). Пока
-- флаги лежали в jsonb самой сессии, замена была переписыванием документа.
-- Со строками на участника нужен признак поколения, иначе «готов» из прошлой
-- проверки досталось бы новой.
CREATE TABLE v2.team_preflight_session (
    team_id             UUID        PRIMARY KEY REFERENCES v2.ranked_team (id) ON DELETE CASCADE,
    session_seq         INTEGER     NOT NULL DEFAULT 1,
    intent              VARCHAR(8)  NOT NULL,
    game_mode           VARCHAR(16) NOT NULL,
    requested_room_id   VARCHAR(24),
    initiator_player_id UUID        NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    target_room_id      VARCHAR(24),
    room_ready          BOOLEAN     NOT NULL DEFAULT FALSE,
    search_started      BOOLEAN     NOT NULL DEFAULT FALSE,
    search_count        INTEGER     NOT NULL DEFAULT 0,
    failed              BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Сессию правят оба напарника и подбор: без @Version «поиск начался»
    -- и «готов» затирают друг друга.
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_team_preflight_intent CHECK (intent IN ('quick', 'room')),
    CONSTRAINT ck_team_preflight_mode   CHECK (game_mode IN ('classic', 'sabotage')),
    -- Замысел room без комнаты уезжал бы к случайным соперникам — ровно та
    -- ошибка, ради которой замысел стал перечислением.
    CONSTRAINT ck_team_preflight_room   CHECK ((intent = 'room') = (requested_room_id IS NOT NULL)),
    CONSTRAINT ck_team_preflight_room_id
        CHECK (requested_room_id IS NULL OR requested_room_id ~ '^hat-[0-9a-f]{16}$'),
    CONSTRAINT ck_team_preflight_target_id
        CHECK (target_room_id IS NULL OR target_room_id ~ '^hat-[0-9a-f]{16}$')
);

-- Уборщику просроченных сессий. Предикат «ещё не истекла» в индекс не
-- поместить: now() не постоянна. Поэтому отсеиваются заведомо мёртвые, а
-- срок сравнивается уже по индексу.
CREATE INDEX ix_team_preflight_open ON v2.team_preflight_session (expires_at) WHERE NOT failed;

-- Готовность одного напарника: строка на человека вместо трёх параллельных
-- jsonb (ready, media, media_at) с uid в ключе. Каждый пишет только свою
-- строку, поэтому @Version здесь не нужен — конфликтовать не с кем.
CREATE TABLE v2.team_preflight_participant (
    team_id     UUID        NOT NULL REFERENCES v2.team_preflight_session (team_id) ON DELETE CASCADE,
    player_id   UUID        NOT NULL,
    session_seq INTEGER     NOT NULL,
    media_ok    BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Признак связи протухает за 25 секунд, и считает срок сервер: у клиента
    -- часы уезжают. Без отметки времени свежесть проверить нечем.
    media_ok_at TIMESTAMPTZ,
    ready       BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_team_preflight_participant PRIMARY KEY (team_id, player_id),
    -- Играть вслепую нельзя: «готов» без подтверждённой связи невозможен.
    -- Сегодня это правило живёт в двух местах кода сразу.
    CONSTRAINT ck_team_preflight_participant_ready CHECK (NOT ready OR media_ok),
    CONSTRAINT ck_team_preflight_participant_media CHECK (NOT media_ok OR media_ok_at IS NOT NULL)
);


-- ═══════════════════════════ rating ═══════════════════════════

-- Таблица сезона и её чемпион.
--
-- Сегодня идентификатор — склеенная строка «2026-winter-sabotage-ru», и
-- собирают её в трёх местах (Seasons.rankingId и два обхода в RatingServiceImpl).
-- Четыре поля стали четырьмя колонками, а ссылаются на доску суррогатом.
--
-- Чемпион — снимок, а не ссылка: сезон закончился, команда могла смениться
-- именем или распасться, и «чемпион 2026-winter» обязан пережить её роспуск.
-- Логотипа среди колонок нет: он весит 280 КБ и берётся у команды, пока она
-- существует.
CREATE TABLE v2.ranking_board (
    board_id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year                INTEGER     NOT NULL,
    season              VARCHAR(8)  NOT NULL,
    mode                VARCHAR(16) NOT NULL,
    division_language   VARCHAR(8)  NOT NULL,
    champion_team_id    UUID,
    champion_name       VARCHAR(30),
    champion_crowned_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_ranking_board            UNIQUE (year, season, mode, division_language),
    -- Границы года — те же, что зажимает SeasonSelection.
    CONSTRAINT ck_ranking_board_year       CHECK (year BETWEEN 2026 AND 2100),
    -- Сезон и режим — закрытые наборы (Seasons.NAMES, Seasons.MODES): опечатка
    -- в них молча заводит призрачную доску, которую никто больше не найдёт.
    CONSTRAINT ck_ranking_board_season     CHECK (season IN ('winter', 'spring', 'summer', 'autumn')),
    CONSTRAINT ck_ranking_board_mode       CHECK (mode IN ('classic', 'sabotage')),
    CONSTRAINT ck_ranking_board_champion   CHECK ((champion_team_id IS NULL) = (champion_crowned_at IS NULL))
);

-- Положение команды в сезоне.
--
-- Ни ника, ни логотипа, ни состава: сегодня season_ranking_team тащит их
-- копиями (name, logo_data_url и два jsonb с составом), и они устаревают в тот
-- же миг, когда команда меняет имя. last_technical_penalty тоже не переехал:
-- он выводится из числа техпоражений (15 → 30 → 50) и никем не читается.
--
-- @Version здесь намеренно нет: очки прибавляются атомарным
-- UPDATE ... SET points = points + ?, и последняя запись верна.
CREATE TABLE v2.season_team_standing (
    board_id           BIGINT      NOT NULL REFERENCES v2.ranking_board (board_id),
    team_id            UUID        NOT NULL,
    points             INTEGER     NOT NULL DEFAULT 0,
    games              INTEGER     NOT NULL DEFAULT 0,
    wins               INTEGER     NOT NULL DEFAULT 0,
    technical_forfeits INTEGER     NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_season_team_standing PRIMARY KEY (board_id, team_id)
);

-- Сама таблица сезона: сотня строк по убыванию очков (findByRankingIdOrderByPointsDesc).
-- team_id третьим полем делает порядок устойчивым при равных очках — иначе
-- две соседние страницы показали бы одну команду дважды.
CREATE INDEX ix_season_team_standing_board ON v2.season_team_standing (board_id, points DESC, team_id);

-- «Все сезоны этой команды»: карточка команды показывает свои показатели.
CREATE INDEX ix_season_team_standing_team ON v2.season_team_standing (team_id, board_id);

-- Личное положение игрока.
--
-- synced_team_points — не денормализация, а состояние правила: личные очки
-- растут на разницу командных с прошлой синхронизации (сегодняшний
-- last_team_points). Без него зачёт второй партии начислил бы игроку все
-- командные очки заново.
CREATE TABLE v2.season_player_standing (
    board_id           BIGINT      NOT NULL REFERENCES v2.ranking_board (board_id),
    player_id          UUID        NOT NULL,
    -- Команда, за которую игрок набрал эти очки. Пусто — играл вне команды.
    team_id            UUID,
    points             INTEGER     NOT NULL DEFAULT 0,
    synced_team_points INTEGER     NOT NULL DEFAULT 0,
    games              INTEGER     NOT NULL DEFAULT 0,
    wins               INTEGER     NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_season_player_standing PRIMARY KEY (board_id, player_id)
);
CREATE INDEX ix_season_player_standing_board  ON v2.season_player_standing (board_id, points DESC, player_id);
CREATE INDEX ix_season_player_standing_player ON v2.season_player_standing (player_id, board_id);

-- Факт зачёта партии: барьер идемпотентности.
--
-- Сегодня ключ — склейка «roomId-gameNumber» в VARCHAR(120), и проверка
-- «уже засчитано?» — чтение перед вставкой. Два клиента, дожавшие кнопку
-- одновременно, начисляли очки дважды. Здесь ключ составной, и второй зачёт
-- отвергает база.
--
-- outcome и technical — разные вещи, вопреки строке §6.3 плана: контракт
-- (SubmittedMatchResultResponseDTO) отдаёт и то и другое, и признак
-- технического завершения есть у засчитанной партии тоже.
CREATE TABLE v2.match_result_event (
    room_id     VARCHAR(24) NOT NULL,
    game_number INTEGER     NOT NULL,
    board_id    BIGINT      NOT NULL REFERENCES v2.ranking_board (board_id),
    outcome     VARCHAR(16) NOT NULL,
    technical   BOOLEAN     NOT NULL DEFAULT FALSE,
    reason      VARCHAR(60),
    recorded_by UUID        NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_match_result_event      PRIMARY KEY (room_id, game_number),
    CONSTRAINT ck_match_result_event_room CHECK (room_id ~ '^hat-[0-9a-f]{16}$'),
    -- «Уже засчитано» состоянием не является: это отсутствие вставки.
    CONSTRAINT ck_match_result_event_outcome CHECK (outcome IN ('recorded', 'annulled')),
    CONSTRAINT ck_match_result_event_game    CHECK (game_number >= 0)
);

-- Лента зачётов доски: последние партии сезона.
CREATE INDEX ix_match_result_event_board ON v2.match_result_event (board_id, recorded_at DESC);

-- Разбор зачёта по командам: место, начисление, виновность.
--
-- Заменяет jsonb culprit_team_ids и восстанавливает то, чего сегодня нет
-- вовсе: сколько именно очков получила команда за эту партию. Сейчас об этом
-- можно судить только по разнице в итоговой таблице.
CREATE TABLE v2.match_result_team (
    room_id      VARCHAR(24) NOT NULL,
    game_number  INTEGER     NOT NULL,
    team_id      UUID        NOT NULL,
    -- Место в партии; пусто у виновной команды — она места не занимает.
    place        INTEGER,
    -- Со знаком: у виновной это штраф 15/30/50 с минусом.
    points_delta INTEGER     NOT NULL DEFAULT 0,
    culprit      BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_match_result_team PRIMARY KEY (room_id, game_number, team_id),
    CONSTRAINT fk_match_result_team_event FOREIGN KEY (room_id, game_number)
        REFERENCES v2.match_result_event (room_id, game_number) ON DELETE CASCADE,
    CONSTRAINT ck_match_result_team_place
        CHECK ((culprit AND place IS NULL) OR (NOT culprit AND place >= 1))
);

-- «История команды»: все её партии сезона.
CREATE INDEX ix_match_result_team_team ON v2.match_result_team (team_id, room_id);


-- ═══════════════════ Что стало с двадцатью jsonb кластера ═══════════════════
--
-- Разложены в строки (18 колонок):
--   friend_link.member_uids                → колонки пары v2.friendship
--   direct_chat.member_uids                → колонки пары v2.direct_chat
--   direct_chat.unread_counts              → v2.direct_chat_member.unread_count
--   direct_chat.unread_for                 → он же (счётчик > 0)
--   direct_chat.last_read_at_ms            → v2.direct_chat_member.last_read_message_id
--   direct_chat.member_nicknames/_avatars  → выброшены: копии карточки игрока
--   direct_chat_message.member_uids        → выброшен: пара есть у переписки
--   direct_chat_message.attachment         → v2.direct_chat_photo + kind + shared_recording_id
--   ranked_team.member_uids/_nicknames     → v2.ranked_team_member (ники выброшены)
--   ranked_team.rating                     → выброшен: вечная тысяча, никем не читается
--   ranked_team_preflight.member_uids      → v2.team_preflight_participant
--   ranked_team_preflight.ready/media/media_at → его же колонки
--   season_ranking.champion                → колонки champion_* доски
--   season_ranking_team.member_uids/_nicknames → выброшены: состав отдаёт команда
--   ranked_result_event.culprit_team_ids   → v2.match_result_team.culprit
--
-- Осталось jsonb: ноль. Открытой структуры в кластере не оказалось ни одной —
-- каждое поле имело фиксированный набор ключей, и «свободным» оно было только
-- потому, что документная модель не умела иначе. Два jsonb на всю базу (§6)
-- живут в чужих кластерах: moderation_action.details и
-- recording_egress_event.payload, где состав полей задаёт внешняя система.
