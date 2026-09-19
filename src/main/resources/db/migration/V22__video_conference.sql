-- Видео-чат: созвон до шестнадцати друзей без запуска игры.
--
-- ─────────────────── Откуда это ───────────────────
-- Возможность придумана в ветке dev фронтенда (SCRUM-18): там она жила на
-- Vercel-функциях и трёх документах Firestore — videoConferences/{id},
-- users/{uid}/conferenceInvites/{id} и videoConferences/{id}/chat/{msg}, —
-- а экран опрашивал их раз в пять и восемь секунд. Здесь та же возможность
-- переезжает в единственную базу и получает живой канал /ws/v2/conference:
-- писатель один — область conference, читатели узнают о записи событием.
--
-- ─────────────────── Три таблицы вместо трёх документов ───────────────────
-- Документ видео-чата держал два списка идентификаторов — participantUids и
-- invitedUids — и записывал их целиком при каждом входе, выходе и приглашении;
-- при двух одновременных «принять» один из принявших пропадал. Здесь участие
-- — строка на человека с состоянием, а оба списка — это выборки по нему:
-- принять приглашение значит поменять одну строку, а не переписать документ.
--
-- Приглашение — не отдельная сущность, а состояние той же строки участия:
-- invited → member (принял) | declined (отклонил); member → left (вышел сам)
-- | removed (выгнал хозяин). Повторное приглашение ушедшего — это возврат
-- строки в invited, и уникальность пары (видео-чат, игрок) не даёт завести
-- двух приглашений одному человеку.
--
-- Ников и аватаров здесь нет нигде: их отдаёт карточка игрока на чтении
-- (PlayerCardPort), как во всём кластере социального.

-- Паспорт видео-чата.
--
-- Идентификатор — строка «vc-<hex16>», как у комнаты «hat-<hex16>»: он живёт в
-- адресе страницы и в ссылке, которую копируют друзьям, и должен читаться
-- глазами. Срок жизни — двенадцать часов от создания: видео-чат не закрывают
-- руками, он просто истекает.
--
-- game_room_id — комната, заведённая «этим составом» из видео-чата; по ней
-- участники переходят в игру. Заводится один раз: пока та комната набирается,
-- повторное нажатие отвечает ею же.
CREATE TABLE v2.video_conference (
    id                   VARCHAR(24) NOT NULL,
    host_player_id       UUID        NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'open',
    game_room_id         VARCHAR(24),
    game_room_created_at TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_video_conference PRIMARY KEY (id),
    CONSTRAINT ck_video_conference_id     CHECK (id ~ '^vc-[a-f0-9]{16}$'),
    CONSTRAINT ck_video_conference_status CHECK (status IN ('open', 'closed')),
    -- Комната и момент её создания появляются вместе.
    CONSTRAINT ck_video_conference_game_room
        CHECK ((game_room_id IS NULL) = (game_room_created_at IS NULL))
);

-- Участие в видео-чате: одна строка на пару (видео-чат, игрок).
--
-- Хозяин тоже здесь, со статусом member с момента создания: список участников
-- — одна выборка без особого случая для хозяина.
--
-- game_room_seat — «этому человеку положено место в заведённой из видео-чата
-- комнате». Отмечается при создании комнаты для тех, кто тогда был в звонке;
-- по этой отметке экран переводит в игру только их, а не всех, кто зайдёт в
-- видео-чат позже.
CREATE TABLE v2.video_conference_member (
    conference_id     VARCHAR(24) NOT NULL REFERENCES v2.video_conference (id) ON DELETE CASCADE,
    player_id         UUID        NOT NULL,
    status            VARCHAR(16) NOT NULL,
    -- Кто позвал; пусто у хозяина.
    inviter_player_id UUID,
    game_room_seat    BOOLEAN     NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at       TIMESTAMPTZ,
    CONSTRAINT pk_video_conference_member PRIMARY KEY (conference_id, player_id),
    CONSTRAINT ck_video_conference_member_status
        CHECK (status IN ('invited', 'member', 'declined', 'left', 'removed'))
);

-- Карточки «вас зовут в видео-чат» на каждой странице портала: свои ждущие
-- приглашения. Частичный индекс — ровно те строки, что показывают.
CREATE INDEX ix_video_conference_member_pending
    ON v2.video_conference_member (player_id) WHERE status = 'invited';

-- Чат видео-чата. Текст либо файл, либо и то и другое; пустое сообщение
-- база не примет. Сам файл лежит в хранилище объектов под ключом
-- «conference/<видео-чат>/<игрок>/…» — ключ строит сервер при выдаче билета
-- на загрузку, и по нему же читается, кто и куда положил файл. Ссылка на
-- скачивание не хранится: подпись хранилища живёт часы, а строка — дольше,
-- поэтому ссылку подписывают заново на каждом чтении.
CREATE TABLE v2.video_conference_message (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conference_id    VARCHAR(24)  NOT NULL REFERENCES v2.video_conference (id) ON DELETE CASCADE,
    sender_player_id UUID         NOT NULL,
    -- 1000 символов — предел поля ввода на экране видео-чата.
    body             VARCHAR(1000),
    file_key         VARCHAR(320),
    file_name        VARCHAR(160),
    file_mime        VARCHAR(120),
    file_size        BIGINT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_video_conference_message_payload CHECK (body IS NOT NULL OR file_key IS NOT NULL),
    -- Имя, тип и размер файла появляются вместе с ключом: без них карточку
    -- вложения нечем подписать.
    CONSTRAINT ck_video_conference_message_file
        CHECK ((file_key IS NULL) = (file_name IS NULL)
           AND (file_key IS NULL) = (file_mime IS NULL)
           AND (file_key IS NULL) = (file_size IS NULL)),
    -- 25 МБ — тот же предел, что проверяет билет на загрузку.
    CONSTRAINT ck_video_conference_message_file_size
        CHECK (file_size IS NULL OR file_size BETWEEN 1 AND 26214400)
);

-- Окно чата листается от свежего: последние сто двадцать сообщений видео-чата.
-- Сортировка по id, а не по времени, — по той же причине, что у переписки:
-- база выдаёт его строго возрастающим, а две отметки времени могут совпасть.
CREATE INDEX ix_video_conference_message_window ON v2.video_conference_message (conference_id, id DESC);
