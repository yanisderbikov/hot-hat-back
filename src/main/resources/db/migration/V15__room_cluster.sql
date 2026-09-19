-- Кластер комнаты, партии и диверсий по §6 плана v2. Тридцать две таблицы
-- вместо сегодняшних семи (room на 103 колонки, room_player на 27,
-- room_spectator, room_team, room_chat_message, room_word_submission,
-- room_invite, sabotage_game_use) и ни одной колонки jsonb вместо двадцати
-- восьми — разбор каждой в конце файла.
--
-- ─────────────────── Главный принцип разреза: частота записи ───────────────────
-- Сегодня всё это одна строка room, и у неё нет частичного обновления:
-- сущность Room не помечена @DynamicUpdate, поэтому ЛЮБАЯ запись уносит в
-- базу все 103 колонки разом. Считаем, что это значит на ходу:
--
--   name, game_mode, capacity, is_private   меняются один раз за жизнь комнаты;
--   phase                                   несколько раз за партию, но её
--                                           пишут ПЯТЬ сервисов сразу;
--   turn_started_at, current_word,
--   turn_guessed_words, bag, words_left     меняются на КАЖДОЕ угаданное
--                                           слово, то есть чаще раза в секунду;
--   sabotage_locks, sabotage_events_recent  на каждый выстрел;
--   last_seen_at (в room_player)            раз в 10 секунд с каждого места.
--
-- Пока это одна строка, засчитанное слово переписывает вместе с собой
-- настройки комнаты, составы команд, приглашения, обойму тест-ботов и мешок
-- целиком — и делает это поверх значений, прочитанных до чужой правки
-- (находка B7 аудита). Строка на 103 колонки с двумя jsonb-мешками не влезает
-- в страницу: каждое такое обновление пишет новую версию строки и гоняет
-- TOAST. Отсюда разрез:
--
--   room                паспорт: пишется при создании и переименовании;
--   room_lifecycle      крупная фаза и номер партии: несколько раз за партию;
--   room_host           хозяйство: редко, но отдельным писателем;
--   room_presence       сердцебиение и устройства: раз в 10 с на участника —
--                       своя таблица с fillfactor 70, как player_presence в V13;
--   match               паспорт партии: один раз на старте, один на финише;
--   match_turn          состояние хода: каждое слово — но это 12 колонок,
--                       а не 103, и рядом с ними нет ни настроек, ни составов;
--   match_word,
--   match_turn_word     слова и их исходы: строки вместо переписывания мешка.
--
-- Тот же приём, что V13 применила к присутствию игрока: горячая часть живёт
-- своей таблицей с fillfactor 70, чтобы обновление проходило внутри страницы
-- (HOT-update) и не трогало индексы.
--
-- ─────────────────── Почему схема v2 ───────────────────
-- Та же причина, что в V11 и V13: семь целевых имён заняты сегодняшними
-- таблицами в public, и они продолжают работать — на них живут /api/db,
-- /api/game и /api/portal. Схема снимается одним ALTER TABLE ... SET SCHEMA
-- в день, когда старые таблицы уйдут.
--
-- ─────────────────── Где здесь внешние ключи и где их нет ───────────────────
-- Внутри кластера ключи расставлены все: комната → места, партия → ходы, ход →
-- слова. Наружу — три разряда колонок без ключа, и у каждого своя причина.
--
-- 1. МЕСТО ЗА СТОЛОМ НЕ ССЫЛАЕТСЯ НА УЧЁТКУ. room_player, room_presence,
--    match_player, match_saboteur, match_player_ammo, match_loadout_slot,
--    match_appeal_vote, match_pause_absentee, word_submission,
--    room_chat_message — здесь player_id может принадлежать тест-боту, а у
--    бота учётки нет и не будет: сегодня его идентификатор выглядит как
--    testbot-<комната>-<номер> (TestBotsServiceImpl:155), в v2 это
--    синтетический uuid из таблицы прогона (кластер admin). Внешний ключ на
--    user_account сделал бы тестовую комнату невозможной физически.
--    Признак бота лежит рядом: room_player.bot, match_player.bot,
--    match_saboteur.is_bot.
--
-- 2. АВТОРСТВО БЕЗ КЛЮЧА — то же правило, что в V13: created_by, closed_by,
--    invited_by, host_player_id, reclaim_player_id, from_player_id,
--    performed_by, started_by, attacker_player_id, target_player_id.
--    RESTRICT превратил бы уборку брошенных гостевых учёток в проверку ссылки
--    по неиндексированной колонке, а SET NULL молча стёр бы автора действия,
--    ради которого журнал и ведётся.
--
-- 3. ЧУЖИЕ КЛАСТЕРЫ, КОТОРЫХ ЕЩЁ НЕТ. meme_id (media) и recording (recording)
--    — таблиц v2.meme и v2.recording не существует. Ключи появятся вместе с
--    ними; ссылаться на сегодняшние meme_library и game_recording нельзя —
--    там другой тип ключа и другой владелец.
--
-- Ключи наружу, которые ЕСТЬ: room.ranked_team_id и room_team.ranked_team_id
-- на v2.ranked_team (область team переехала, таблица живая), а также
-- player_id тех трёх таблиц кластера диверсий, где бота быть не может по
-- определению, — это право учётки, а не место за столом.
--
-- ─────────────────── Данные не переносятся ───────────────────
-- Старые таблицы остаются как есть, новые пусты. Область не переключена: этот
-- шаг заводит схему и сущности, переезд кода — отдельная работа, и делается
-- он вместе с переводом фронта (правило, проверенное на команде: созданная
-- через v2 команда была не видна старому POST /api/portal).

CREATE SCHEMA IF NOT EXISTS v2;


-- ═══════════════════════════ room ═══════════════════════════

-- Паспорт комнаты: то, что задают при создании и почти никогда не меняют.
--
-- Из 103 колонок сегодняшней room здесь осталось четырнадцать. Всё остальное
-- либо уехало в таблицы ниже по частоте записи, либо выводится, либо мертво
-- (разбор в конце файла).
--
-- id остаётся кодом, а не суррогатом (§6.1 плана): он ходит по личным чатам
-- ссылкой-приглашением и печатается в адресной строке. Формат тот же, что
-- проверяет @RoomId на входе и CHECK в v2.player_presence.
--
-- kind вместо трёх булевых флагов is_test_room + team_lobby +
-- managed_matchmaking. Флаги позволяли выразить бессмыслицу — тестовое лобби
-- команды под управлением подбора, — а перечисление её не позволяет.
--
-- created_by НЕИЗМЕНЯЕМО и это главное здесь. Сегодня хозяйство комнаты
-- хранится в той же колонке created_by, и setup_host_watch с
-- manual_host_transfer её переписывают. Из-за этого дыра A1 аудита («сервер
-- верит клиенту, кто хозяин») означает буквально захват комнаты: назвавшись
-- хозяином, чужой человек становится её автором. Здесь авторство неизменяемо,
-- а хозяйство — отдельная строка room_host со своим писателем.
CREATE TABLE v2.room (
    id                VARCHAR(24)  NOT NULL,
    -- regular | test | team | matchmaking
    kind              VARCHAR(16)  NOT NULL DEFAULT 'regular',
    name              VARCHAR(80)  NOT NULL,
    -- Одна вместимость вместо max_players и max_participants. Сегодня их две,
    -- и они уже разошлись: RoomProjections берёт вместимость то из одной, то
    -- из другой, а лобби команды ставит только вторую (EnsureTeamLobbyUseCase).
    capacity          INTEGER      NOT NULL DEFAULT 10,
    -- Миллисекунды вместо пары turn_duration (секунды, integer) и
    -- turn_duration_seconds (double). Два представления одной величины уже
    -- расходились: секунды целые, а после паузы длительность хода дробная.
    turn_duration_ms  INTEGER      NOT NULL DEFAULT 60000,
    -- classic | sabotage — тот же набор, что GameMode в контракте.
    game_mode         VARCHAR(16)  NOT NULL DEFAULT 'classic',
    ranked            BOOLEAN      NOT NULL DEFAULT FALSE,
    private_room      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Дивизион комнаты и язык слов — разные вещи: у быстрой комнаты язык слов
    -- может отличаться от дивизиона, по которому пускают в рейтинговую партию.
    -- Списка девяти языков в CHECK нет намеренно, по той же причине, что в
    -- V13: справочник дивизионов живёт в файлах public/i18n, и копия набора в
    -- базе разошлась бы с ними на первом же добавленном языке.
    division_language VARCHAR(8)   NOT NULL DEFAULT 'ru',
    game_language     VARCHAR(8)   NOT NULL DEFAULT 'ru',
    -- Комната, заведённая рейтинговой командой. SET NULL, а не CASCADE:
    -- роспуск команды не должен уносить комнату вместе с идущей партией.
    ranked_team_id    UUID         REFERENCES v2.ranked_team (id) ON DELETE SET NULL,
    created_by        UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_room PRIMARY KEY (id),
    CONSTRAINT ck_room_id CHECK (id ~ '^hat-[0-9a-f]{16}$'),
    CONSTRAINT ck_room_kind CHECK (kind IN ('regular', 'test', 'team', 'matchmaking')),
    CONSTRAINT ck_room_mode CHECK (game_mode IN ('classic', 'sabotage')),
    -- Нижняя граница 2, а не 4 из CreateRoomRequestDTO: лобби рейтинговой
    -- команды ставит вместимость 2, и оно тоже комната.
    CONSTRAINT ck_room_capacity CHECK (capacity BETWEEN 2 AND 10),
    CONSTRAINT ck_room_turn_duration CHECK (turn_duration_ms BETWEEN 10000 AND 600000)
);

-- Витрина открытых комнат отбирает по языку слов и никогда не показывает
-- приватные. Частичный индекс поэтому и частичный: приватных комнат в выборке
-- не бывает ни при каком фильтре.
CREATE INDEX ix_room_public ON v2.room (game_language) WHERE NOT private_room;

-- «Есть ли у этой команды живая комната» — вопрос префлайта.
CREATE INDEX ix_room_ranked_team ON v2.room (ranked_team_id) WHERE ranked_team_id IS NOT NULL;


-- Крупная фаза комнаты и счётчик партий.
--
-- Это половина разрезанной room.phase. Сегодня одна колонка на 24 символа
-- держит восемь значений двух разных уровней сразу — «комната набирается» и
-- «идёт апелляция», — и пишут её пять сервисов: TeamServiceImpl:320,
-- MatchmakingServiceImpl:168,473, AdminServiceImpl:259, GameServiceImpl:266,
-- 705,1570, TestBotsServiceImpl:297,392,407,516. Пока значения двух уровней
-- лежали в одной колонке, правило «один писатель» было невозможно физически.
--
-- Здесь остался только уровень комнаты. Состояние хода — match_turn.state,
-- факт завершения партии — match.state, а фаза контракта (RoomPhase из восьми
-- значений) собирается проекцией из трёх таблиц. Прямого преемника у phase
-- нет и не должно быть.
--
-- game_number поднимается атомарным UPDATE ... SET game_number = game_number+1
-- под блокировкой строки (§6.4): две вкладки хозяина иначе заведут две партии
-- с одним номером. Сегодня от этого спасает только claimGameTab в localStorage
-- браузера.
CREATE TABLE v2.room_lifecycle (
    room_id          VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    -- setup | playing | resetting | closed
    state            VARCHAR(16) NOT NULL DEFAULT 'setup',
    game_number      INTEGER     NOT NULL DEFAULT 0,
    -- Питает уборку брошенных комнат. Отдельной колонкой, а не max() по
    -- присутствию: комната живёт и пока в ней просто болтают в чате.
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ,
    closed_by        UUID,
    closed_reason    VARCHAR(40),
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_room_lifecycle PRIMARY KEY (room_id),
    CONSTRAINT ck_room_lifecycle_state CHECK (state IN ('setup', 'playing', 'resetting', 'closed')),
    CONSTRAINT ck_room_lifecycle_closed CHECK ((state = 'closed') = (closed_at IS NOT NULL)),
    CONSTRAINT ck_room_lifecycle_number CHECK (game_number >= 0)
);

-- Витрина лобби и уборка смотрят только на живые комнаты, а закрытых со
-- временем становится большинство — отсюда частичные индексы.
CREATE INDEX ix_room_lifecycle_open ON v2.room_lifecycle (state) WHERE state <> 'closed';
CREATE INDEX ix_room_lifecycle_idle ON v2.room_lifecycle (last_activity_at) WHERE state <> 'closed';


-- Кто хозяин комнаты сейчас.
--
-- Вторая половина разрезанного created_by. Хозяйство меняется передачей
-- вручную и по бездействию, авторство — никогда, и держать их в одной колонке
-- значит не уметь ответить «кто завёл комнату» после первой же передачи.
--
-- reclaim_player_id — прежний хозяин, за которым право вернуться. Сегодня это
-- previous_host_uid рядом с host_transferred_at, host_transfer_type и
-- host_transferred_by: четыре колонки в строке комнаты, из которых три
-- описывают одно прошедшее событие. Событие уехало в журнал ниже.
CREATE TABLE v2.room_host (
    room_id            VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    host_player_id     UUID        NOT NULL,
    reclaim_player_id  UUID,
    -- Сердцебиение хозяина: по нему считается бездействие и передаётся
    -- хозяйство. Пишется чаще всего в этой таблице, потому и лежит здесь, а не
    -- в паспорте комнаты.
    last_activity_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Чем именно хозяин подтвердил присутствие (HostActivityKind). CHECK'а
    -- нет намеренно: набор закрывает перечисление на входе, а здесь это
    -- пометка в журнале, а не состояние, — новый вид активности не должен
    -- стоить миграции.
    last_activity_kind VARCHAR(16),
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_room_host PRIMARY KEY (room_id)
)
-- fillfactor 80, а не 70: сердцебиение хозяина приходит раз в несколько
-- секунд — реже, чем с каждого места, но чаще, чем меняется что-либо ещё в
-- строке. Запас под HOT-update нужен, а отдавать под него треть страницы у
-- таблицы с одной строкой на комнату незачем.
WITH (fillfactor = 80);

-- «Где я хозяин» — вопрос портала при восстановлении сессии.
CREATE INDEX ix_room_host_player ON v2.room_host (host_player_id);


-- История передач хозяйства.
--
-- Отдельной таблицей, потому что сегодня прошедшее событие описано тремя
-- колонками ЖИВОЙ строки комнаты (host_transferred_at, host_transfer_type,
-- host_transferred_by) и вторая передача стирает первую. Спор «кто и когда
-- забрал у меня комнату» этими колонками неразрешим.
CREATE TABLE v2.room_host_transfer (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY,
    room_id        VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    -- Пусто у самой первой записи: до неё хозяина не было, была только запись
    -- о создании комнаты.
    from_player_id UUID,
    to_player_id   UUID        NOT NULL,
    -- manual | idle | departure. Набор не закрыт CHECK: это причина в
    -- журнале, а не состояние.
    reason         VARCHAR(24) NOT NULL,
    performed_by   UUID,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_room_host_transfer PRIMARY KEY (id)
);

CREATE INDEX ix_room_host_transfer_room ON v2.room_host_transfer (room_id, occurred_at DESC);


-- Команда как место в составе комнаты.
--
-- Объявлена раньше room_player, потому что место игрока на неё ссылается.
--
-- Колонки score здесь нет: §6.3 плана числит её выводимой, и это буквально
-- так — счёт команды есть число незачёркнутых угаданных слов её ходов
-- (match_turn_word). Денормализованный счётчик рядом с исходными строками
-- умеет с ними разойтись, а разойдясь, отдаёт игроку неверный счёт партии.
--
-- Колонки member_uids (jsonb) тоже нет: состав — это room_player.team_id.
-- Сегодня он лежит в двух местах сразу, и они расходятся.
CREATE TABLE v2.room_team (
    id             UUID        NOT NULL,
    room_id        VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    -- Место в очереди ходов. Сегодня очередь — это отдельный jsonb team_order
    -- в строке комнаты, то есть второй источник правды о порядке.
    slot           INTEGER     NOT NULL,
    name           VARCHAR(40),
    -- Команда комнаты, за которой стоит постоянная рейтинговая команда.
    ranked_team_id UUID        REFERENCES v2.ranked_team (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_room_team PRIMARY KEY (id),
    CONSTRAINT ux_room_team_slot UNIQUE (room_id, slot),
    CONSTRAINT ck_room_team_slot CHECK (slot BETWEEN 0 AND 9)
);
-- Отдельного индекса «команды комнаты» нет: ux_room_team_slot начинается с
-- room_id и обслуживает и выборку по комнате, и порядок ходов.


-- Место игрока: чьё оно и в какой команде.
--
-- Здесь нет name и avatar_data_url. Сегодня они есть, и это самая дорогая
-- копия чужих данных во всей базе: аватар до 140 000 символов лежит В КАЖДОЙ
-- строке места, тиражируется в каждый снимок комнаты и в состояние рекордера,
-- которое опрашивается четыре раза в секунду. Имя и аватар читаются через
-- ProfileDirectoryPort; вместе с копиями исчезает propagateNickname
-- (ProfileServiceImpl:198-260), обходивший на каждую смену ника все комнаты
-- игрока.
--
-- Здесь нет и колонок диверсий (arsenal, meme_loadout, used_meme_ids,
-- meme_available_ids, meme_reserve_ids, meme_recycle_queue, meme_cycle_cursor,
-- sabotage_cooldown_until): боезапас принадлежит партии, а не месту в
-- комнате, и живёт в кластере диверсий. Сегодня выход из комнаты и возврат
-- обнуляют боезапас посреди партии просто потому, что он лежал в строке места.
CREATE TABLE v2.room_player (
    room_id    VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    player_id  UUID        NOT NULL,
    -- Пусто — игрок вошёл, но за стол ещё не сел. SET NULL: роспуск команды
    -- поднимает её игроков обратно в зал, а не выкидывает из комнаты.
    team_id    UUID        REFERENCES v2.room_team (id) ON DELETE SET NULL,
    bot        BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Кто позвал. Заменяет пару invited_by + invite_id: сама заявка живёт в
    -- room_invite, и хранить рядом с местом ещё и её номер незачем.
    invited_by UUID,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_room_player PRIMARY KEY (room_id, player_id)
);

-- «В каких комнатах этот игрок» — восстановление сессии и запрет на два места.
CREATE INDEX ix_room_player_player ON v2.room_player (player_id);
-- Состав команды за один проход.
CREATE INDEX ix_room_player_team ON v2.room_player (room_id, team_id);


-- Место зрителя.
--
-- Внешний ключ на учётку здесь ЕСТЬ, в отличие от места игрока: зрителем
-- бывает только человек, ботов в зал не сажают. Каскад означает, что удаление
-- учётки уносит её места, а не оставляет сиротами.
CREATE TABLE v2.room_spectator (
    room_id   VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    player_id UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version   BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_room_spectator PRIMARY KEY (room_id, player_id)
);

CREATE INDEX ix_room_spectator_player ON v2.room_spectator (player_id);


-- Сердцебиение и состояние устройств. Самая горячая таблица кластера.
--
-- Отметка приходит с каждого места примерно раз в 10 секунд, а состояние
-- камеры и микрофона — на каждое переключение. Пока это были колонки
-- room_player.last_seen_at/camera_enabled/microphone_enabled, каждый такой
-- пинг переписывал строку места вместе с аватаром на 140 000 символов и
-- боезапасом.
--
-- Строка общая для игрока и для зрителя: зритель тоже шлёт отметку
-- (livekit.js:3161), и заводить ей вторую таблицу значило бы объединять их
-- через UNION в каждом подсчёте живых. Внешнего ключа на room_player нет
-- именно поэтому.
--
-- @Version здесь намеренно нет (§6.4): побеждает последняя запись, и это
-- верная семантика — «видели в 12:00:30» после «видели в 12:00:00» не
-- конфликт, а обновление.
--
-- fillfactor 70 — та же причина, что у v2.player_presence в V13: место под
-- новые версии строк в самой странице, чтобы обновление шло HOT-путём и не
-- трогало индексы.
CREATE TABLE v2.room_presence (
    room_id        VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    player_id      UUID        NOT NULL,
    last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    camera_on      BOOLEAN     NOT NULL DEFAULT FALSE,
    mic_on         BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Поколение медиа-настроек: по нему собеседники понимают, что дорожку
    -- нужно пересобрать. Растёт атомарным UPDATE.
    media_revision BIGINT      NOT NULL DEFAULT 0,
    media_ready_at TIMESTAMPTZ,
    CONSTRAINT pk_room_presence PRIMARY KEY (room_id, player_id)
) WITH (fillfactor = 70);

-- «Кто в комнате жив прямо сейчас» — окно живости считает сервер.
CREATE INDEX ix_room_presence_seen ON v2.room_presence (room_id, last_seen_at DESC);


-- Счётчик живых игроков для витрины.
--
-- Его пишет хозяин комнаты раз в несколько секунд (сегодня — колонки
-- public_active_players и public_presence_at, добавленные V9), а читает
-- главная страница списком по всем комнатам сразу. Отдельной таблицей,
-- потому что это единственное поле комнаты, которое пишет один участник, а
-- читают все, и держать его в паспорте значит переписывать паспорт по
-- таймеру.
CREATE TABLE v2.room_presence_counter (
    room_id        VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    active_players INTEGER     NOT NULL DEFAULT 0,
    measured_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_room_presence_counter PRIMARY KEY (room_id),
    CONSTRAINT ck_room_presence_counter_players CHECK (active_players >= 0)
) WITH (fillfactor = 70);

-- Витрине нужны только комнаты, где кто-то есть, и только свежие показания.
CREATE INDEX ix_room_presence_counter_live
    ON v2.room_presence_counter (measured_at DESC) WHERE active_players > 0;


-- Сообщение чата комнаты.
--
-- author_name — снимок, а не копия чужих данных: реплика должна остаться
-- подписанной тем именем, под которым её написали, даже если человек потом
-- сменил ник или вышел из комнаты. Тем же снимком закрыт и bot: имя тест-бота
-- нигде больше не хранится.
--
-- author_seat заменяет пару role + is_test_bot. Три значения вместо строки
-- роли и булева флага рядом: у флага и роли было четыре сочетания, из которых
-- осмысленны три.
CREATE TABLE v2.room_chat_message (
    id               UUID         NOT NULL,
    room_id          VARCHAR(24)  NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    -- Пусто у системного сообщения комнаты: его автор — сервер.
    author_player_id UUID,
    author_name      VARCHAR(40)  NOT NULL,
    -- player | spectator | bot
    author_seat      VARCHAR(16)  NOT NULL,
    body             VARCHAR(800),
    -- Картинка сообщения — это его содержимое, а не копия чужих данных,
    -- поэтому лежит здесь, а не за ссылкой. Предел тот же, что проверяет
    -- PostRoomChatImageRequestDTO на входе.
    image_data_url   TEXT,
    image_width      INTEGER,
    image_height     INTEGER,
    image_file_name  VARCHAR(80),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    edited_at        TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_room_chat_message PRIMARY KEY (id),
    CONSTRAINT ck_room_chat_message_seat CHECK (author_seat IN ('player', 'spectator', 'bot')),
    -- Пустое сообщение отправить нельзя: либо текст, либо картинка.
    CONSTRAINT ck_room_chat_message_payload CHECK (body IS NOT NULL OR image_data_url IS NOT NULL),
    -- Размеры и картинка появляются вместе: без них экран не знает, сколько
    -- места занять до загрузки, и лента прыгает.
    CONSTRAINT ck_room_chat_message_image
        CHECK ((image_data_url IS NULL) = (image_width IS NULL)
           AND (image_data_url IS NULL) = (image_height IS NULL)),
    CONSTRAINT ck_room_chat_message_image_size
        CHECK (image_data_url IS NULL OR octet_length(image_data_url) <= 160000)
);

-- Лента чата читается страницами от новых к старым.
CREATE INDEX ix_room_chat_message_room ON v2.room_chat_message (room_id, created_at DESC);


-- Приглашение друга в комнату.
--
-- game_number_at_invite гасит приглашение, выданное до старта партии: войти
-- по нему в уже начатую игру нельзя. Это единственное, ради чего номер здесь
-- нужен, и он же делает состояние вычислимым без сторожа.
--
-- Ссылки на сообщение чата здесь НЕТ намеренно, хотя §6.2 плана её называет:
-- обратная ссылка уже есть — v2.direct_chat_message.room_invite_id, заведённая
-- V11. Две взаимные ссылки на один факт — ровно то, что этот план убирает из
-- схемы; при удалении сообщения они разошлись бы.
--
-- Внешний ключ на получателя есть (приглашают человека, не бота), на
-- отправителя нет (авторство).
CREATE TABLE v2.room_invite (
    id                    UUID        NOT NULL,
    room_id               VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    from_player_id        UUID        NOT NULL,
    to_player_id          UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    -- pending | accepted | expired. Остальные значения RoomInviteState
    -- (missing, forbidden, roomMissing, roomClosed, gameStarted, unavailable)
    -- не хранятся: это ответы проекции на вопрос «можно ли войти сейчас»,
    -- и вычисляются они из комнаты и её жизненного цикла.
    state                 VARCHAR(16) NOT NULL DEFAULT 'pending',
    game_number_at_invite  INTEGER     NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at            TIMESTAMPTZ NOT NULL,
    accepted_at           TIMESTAMPTZ,
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_room_invite PRIMARY KEY (id),
    CONSTRAINT ck_room_invite_state CHECK (state IN ('pending', 'accepted', 'expired')),
    CONSTRAINT ck_room_invite_accepted CHECK ((state = 'accepted') = (accepted_at IS NOT NULL))
);

-- Входящие приглашения игрока: экран показывает их от новых к старым.
CREATE INDEX ix_room_invite_inbox ON v2.room_invite (to_player_id, state, created_at DESC);
-- Погашение просроченных: только ждущие, остальных со временем большинство.
CREATE INDEX ix_room_invite_expiry ON v2.room_invite (expires_at) WHERE state = 'pending';


-- ═══════════════════════════ game ═══════════════════════════

-- Слово, сданное в шляпу до старта партии.
--
-- Сегодня это room_word_submission: одна строка на игрока с jsonb-массивом
-- слов внутри и счётчиком word_count рядом. Из-за массива «убрать одно своё
-- слово» — это переписать весь список, а два одновременных пополнения теряют
-- одно из них целиком.
--
-- normalized считает база тем же выражением, что и ключ ника в V13. Поэтому
-- «Кот», «кот » и «кот» — одно слово, и узнаёт это уникальный индекс, а не
-- чтение перед вставкой.
CREATE TABLE v2.word_submission (
    id               UUID        NOT NULL,
    room_id          VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    author_player_id UUID        NOT NULL,
    -- 80 знаков — предел SubmitWordsRequestDTO.
    word             VARCHAR(80) NOT NULL,
    normalized       VARCHAR(80) GENERATED ALWAYS AS (lower(btrim(word))) STORED,
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_word_submission PRIMARY KEY (id),
    CONSTRAINT ck_word_submission_word CHECK (btrim(word) <> '')
);

-- Одно и то же слово дважды от одного автора — 409, а не молча проглоченный
-- дубль в шляпе. Индекс отвечает и на «сколько слов сдал этот игрок», и на
-- «все слова комнаты»: оба вопроса — его префиксы, поэтому отдельных индексов
-- (room_id) и (room_id, author), названных в §6.2, здесь нет.
CREATE UNIQUE INDEX ux_word_submission ON v2.word_submission (room_id, author_player_id, normalized);


-- Партия как факт.
--
-- Замороженные условия (game_mode, ranked, test_room, game_language,
-- turn_duration_ms) скопированы из комнаты намеренно, и это не копия чужих
-- данных: партия играется по правилам, действовавшим на её старте. Сменить
-- режим комнаты посреди партии и получить другой подсчёт очков задним числом
-- нельзя именно потому, что партия смотрит на свою копию.
--
-- Техническое завершение развёрнуто в колонки. Сегодня это jsonb
-- technical_termination, и «была ли партия аннулирована» приходится узнавать
-- разбором json в SQL.
CREATE TABLE v2.match (
    id                     UUID        NOT NULL,
    room_id                VARCHAR(24) NOT NULL REFERENCES v2.room (id) ON DELETE CASCADE,
    game_number            INTEGER     NOT NULL,
    game_mode              VARCHAR(16) NOT NULL,
    ranked                 BOOLEAN     NOT NULL DEFAULT FALSE,
    test_room              BOOLEAN     NOT NULL DEFAULT FALSE,
    game_language          VARCHAR(8)  NOT NULL,
    turn_duration_ms       INTEGER     NOT NULL,
    -- running | finished
    state                  VARCHAR(16) NOT NULL DEFAULT 'running',
    -- bag_empty | technical | room_closed | host_reset. Набор не закрыт
    -- CHECK: это причина в протоколе, а не состояние.
    finish_reason          VARCHAR(24),
    -- ranked_disconnect | casual_disconnect — те же два значения, что отдаёт
    -- TechnicalTerminationView. Кого именно не хватило — строки
    -- match_pause_absentee, а не второй jsonb-массив имён.
    termination_kind       VARCHAR(24),
    termination_no_penalty BOOLEAN     NOT NULL DEFAULT FALSE,
    started_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at            TIMESTAMPTZ,
    version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_match PRIMARY KEY (id),
    -- Барьер идемпотентности старта: тот же ключ, по которому рейтинг
    -- засчитывает результат (match_result_event из V11).
    CONSTRAINT ux_match_number UNIQUE (room_id, game_number),
    CONSTRAINT ck_match_state CHECK (state IN ('running', 'finished')),
    CONSTRAINT ck_match_mode CHECK (game_mode IN ('classic', 'sabotage')),
    CONSTRAINT ck_match_finished
        CHECK ((state = 'finished') = (finished_at IS NOT NULL)
           AND (state = 'finished') = (finish_reason IS NOT NULL)),
    CONSTRAINT ck_match_termination
        CHECK (termination_kind IS NULL OR termination_kind IN ('ranked_disconnect', 'casual_disconnect'))
);

-- В комнате идёт не больше одной партии. Это тот самый инвариант, который
-- сегодня держится клиентским claimGameTab в localStorage: две вкладки
-- хозяина заводили две партии, и вторая молча затирала первую.
CREATE UNIQUE INDEX ux_match_running ON v2.match (room_id) WHERE state = 'running';

-- Лента сыгранных рейтинговых партий (консоль и разбор сезона).
CREATE INDEX ix_match_finished ON v2.match (ranked, finished_at DESC) WHERE state = 'finished';


-- Команда партии и её место в очереди ходов.
--
-- Очередь — колонка turn_order, а не jsonb team_order в строке комнаты.
-- Уникальность порядка внутри партии делает невозможной «две команды на
-- третьем месте», которую массив допускал.
CREATE TABLE v2.match_team (
    match_id     UUID     NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    room_team_id UUID     NOT NULL REFERENCES v2.room_team (id) ON DELETE CASCADE,
    turn_order   INTEGER  NOT NULL,
    CONSTRAINT pk_match_team PRIMARY KEY (match_id, room_team_id),
    CONSTRAINT ux_match_team_order UNIQUE (match_id, turn_order),
    CONSTRAINT ck_match_team_order CHECK (turn_order >= 0)
);
-- Отдельного индекса (match_id, turn_order) нет: ux_match_team_order — это он.


-- Замороженный на старте ростер.
--
-- Заменяет два jsonb сразу: team_rosters (составы) и game_player_names_by_uid
-- (имена). Партия играется теми, кто был в ней на старте: зашедший позже
-- зритель игроком не становится, а вышедший игрок остаётся в протоколе.
--
-- display_name — снимок, и это не та копия чужих данных, которую убирает
-- §6.3. Разница простая: room_player.name был копией ЖИВОГО ника и обязан был
-- обновляться при его смене (отсюда propagateNickname по всем комнатам), а
-- здесь имя заморожено намеренно — оно должно остаться прежним, даже когда
-- человек сменит ник или удалит учётку.
CREATE TABLE v2.match_player (
    match_id     UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    player_id    UUID        NOT NULL,
    room_team_id UUID        NOT NULL REFERENCES v2.room_team (id) ON DELETE CASCADE,
    display_name VARCHAR(40) NOT NULL,
    -- Место внутри команды: по нему считается, кто объясняет, а кто угадывает.
    seat_in_team INTEGER     NOT NULL,
    bot          BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_match_player PRIMARY KEY (match_id, player_id),
    CONSTRAINT ck_match_player_seat CHECK (seat_in_team >= 0)
);

-- Состав команды в партии — за один проход.
CREATE INDEX ix_match_player_team ON v2.match_player (match_id, room_team_id);
-- «В каких партиях я играл» — история игрока и проверка прав в снимке.
CREATE INDEX ix_match_player_player ON v2.match_player (player_id, match_id);


-- Слово в шляпе и его место в колоде.
--
-- Заменяет jsonb bag: массив строк в строке комнаты, который переписывался
-- целиком на каждое вытянутое слово вместе со всеми 103 колонками. Здесь
-- вытянуть слово — это UPDATE одной строки по индексу.
--
-- bag_position задаёт порядок. Пропущенное слово возвращается в шляпу с
-- позицией max+1 под той же блокировкой хода (§6.4), поэтому оно не выпадет
-- следующим же нажатием — сегодня перетасовка всего массива этого не
-- гарантировала.
--
-- @Version нет намеренно: строку слова трогает только сценарий хода, уже
-- держащий блокировку match_turn.
CREATE TABLE v2.match_word (
    id           UUID        NOT NULL,
    match_id     UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    word         VARCHAR(80) NOT NULL,
    bag_position INTEGER     NOT NULL,
    -- in_bag | drawn | guessed. Пропущенное слово — это снова in_bag с новой
    -- позицией, отдельного состояния ему не нужно; отменённое апелляцией —
    -- тоже, оно возвращается в шляпу.
    state        VARCHAR(16) NOT NULL DEFAULT 'in_bag',
    -- Кто сдал это слово. Нужно, чтобы вернуть слова в шляпу при роспуске
    -- партии и чтобы не показывать игроку его собственное слово первым.
    submitted_by UUID,
    CONSTRAINT pk_match_word PRIMARY KEY (id),
    CONSTRAINT ux_match_word_position UNIQUE (match_id, bag_position),
    CONSTRAINT ck_match_word_state CHECK (state IN ('in_bag', 'drawn', 'guessed'))
);

-- «Следующее слово» — минимальная позиция среди лежащих в шляпе. Частичный
-- индекс, потому что по мере партии таких строк остаётся всё меньше, а
-- вопрос задаётся на каждое слово.
CREATE INDEX ix_match_word_bag ON v2.match_word (match_id, bag_position) WHERE state = 'in_bag';


-- Ход как сущность.
--
-- Сегодня ход — это одиннадцать колонок строки комнаты (turn_id, turn_started_at,
-- turn_ends_at, current_team_id, current_team_index, current_word,
-- current_turn_score, explainer_uid, explainer_name, guesser_uid, guesser_name)
-- плюс jsonb turn_guessed_words. Каждое угаданное слово переписывало их вместе
-- со всей комнатой.
--
-- current_turn_score колонки здесь нет: счёт хода есть число незачёркнутых
-- строк match_turn_word этого хода. Денормализованному счётчику рядом с ними
-- расходиться нечем, потому что его нет.
--
-- explainer_name/guesser_name тоже нет: имена лежат в замороженном ростере.
CREATE TABLE v2.match_turn (
    id                  UUID        NOT NULL,
    match_id            UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    turn_no             INTEGER     NOT NULL,
    room_team_id        UUID        NOT NULL REFERENCES v2.room_team (id) ON DELETE CASCADE,
    explainer_player_id UUID        NOT NULL,
    -- Пусто, если в команде остался один игрок: угадывать некому.
    guesser_player_id   UUID,
    -- intro | active | appeal | review | closed
    state               VARCHAR(16) NOT NULL DEFAULT 'intro',
    -- Слово на руках у объясняющего. SET NULL, а не CASCADE: слово может уйти
    -- из партии (роспуск), и это не повод удалять ход.
    current_word_id     UUID        REFERENCES v2.match_word (id) ON DELETE SET NULL,
    started_at          TIMESTAMPTZ,
    -- Дедлайн по серверным часам. Сегодня рядом живут turn_started_at
    -- (timestamptz), turn_ends_at (bigint миллисекунд) и turn_duration_seconds
    -- (double) — три представления одного отрезка времени, и они расходились
    -- после каждой паузы.
    deadline_at         TIMESTAMPTZ,
    -- Длительность именно этого хода: после паузы она равна остатку.
    duration_ms         INTEGER     NOT NULL,
    closed_at           TIMESTAMPTZ,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_match_turn PRIMARY KEY (id),
    CONSTRAINT ux_match_turn_no UNIQUE (match_id, turn_no),
    CONSTRAINT ck_match_turn_state CHECK (state IN ('intro', 'active', 'appeal', 'review', 'closed')),
    -- Ход, который ещё не начали, не имеет ни начала, ни дедлайна.
    CONSTRAINT ck_match_turn_started
        CHECK ((state = 'intro') = (started_at IS NULL)
           AND (state = 'intro') = (deadline_at IS NULL)),
    CONSTRAINT ck_match_turn_closed CHECK ((state = 'closed') = (closed_at IS NOT NULL))
);

-- В партии не больше одного незакрытого хода. Сегодня «ход уже сменился»
-- узнаётся сравнением turn_id в теле запроса — то есть на честном слове
-- клиента; здесь это инвариант базы.
CREATE UNIQUE INDEX ux_match_turn_open ON v2.match_turn (match_id) WHERE state <> 'closed';
-- Отдельного индекса (match_id, turn_no DESC) нет: ux_match_turn_no
-- обслуживает и его — btree читается в обе стороны.


-- Исход слова в ходе.
--
-- Заменяет jsonb turn_guessed_words, где пропущенные слова лежали в том же
-- массиве с флагом skipped.
--
-- КЛЮЧ ЗДЕСЬ СОСТАВНОЙ, И ЭТО ОСОЗНАННОЕ ОТСТУПЛЕНИЕ ОТ §6.2 плана, который
-- называет ключом один лишь id. Причина: id придумывает КЛИЕНТ и присылает в
-- адресе (@Pattern ^[A-Za-z0-9_-]{6,80}$ в TurnController) — это ключ
-- идемпотентности нажатия, а не суррогат. Шести знаков достаточно, чтобы два
-- клиента в разных комнатах выбрали одну строку, и при глобальном ключе такое
-- совпадение дало бы не ошибку, а ХУДШЕЕ: сценарий счёл бы чужую запись
-- повтором и не начислил очко. Ключ (turn_id, id) делает идемпотентность
-- ровно такой, какая нужна, — в пределах хода.
--
-- invalidated вместо удаления строки: слово, отменённое апелляцией, обязано
-- остаться видимым в разборе хода — иначе непонятно, за что отняли очко.
CREATE TABLE v2.match_turn_word (
    turn_id       UUID        NOT NULL REFERENCES v2.match_turn (id) ON DELETE CASCADE,
    id            VARCHAR(80) NOT NULL,
    match_word_id UUID        NOT NULL REFERENCES v2.match_word (id) ON DELETE CASCADE,
    -- guessed | skipped
    outcome       VARCHAR(8)  NOT NULL,
    -- Порядок внутри хода: по нему собирается разбор и считаются награды.
    ordinal       INTEGER     NOT NULL,
    invalidated   BOOLEAN     NOT NULL DEFAULT FALSE,
    resolved_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_match_turn_word PRIMARY KEY (turn_id, id),
    CONSTRAINT ux_match_turn_word_ordinal UNIQUE (turn_id, ordinal),
    CONSTRAINT ck_match_turn_word_id CHECK (id ~ '^[A-Za-z0-9_-]{6,80}$'),
    CONSTRAINT ck_match_turn_word_outcome CHECK (outcome IN ('guessed', 'skipped'))
);

-- Счёт хода и счёт команды: угаданные и не отменённые.
CREATE INDEX ix_match_turn_word_scored
    ON v2.match_turn_word (turn_id, outcome) WHERE NOT invalidated;
-- Список слов на голосование: только угаданные, от последнего к первому.
CREATE INDEX ix_match_turn_word_appeal
    ON v2.match_turn_word (turn_id, resolved_at DESC) WHERE outcome = 'guessed';


-- Окно апелляции по ходу: строка есть — окно открыто.
--
-- eligible_count и majority колонками не заведены, хотя AppealView их
-- отдаёт: и то и другое считается по match_player («все, кроме команды,
-- которая ходила»), а хранить рядом со списком его же размер — это счётчик,
-- который однажды разойдётся со списком.
CREATE TABLE v2.match_appeal (
    turn_id   UUID        NOT NULL REFERENCES v2.match_turn (id) ON DELETE CASCADE,
    match_id  UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    ends_at   TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    version   BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_match_appeal PRIMARY KEY (turn_id)
);

-- «Идёт ли сейчас апелляция в этой партии» — вопрос каждого снимка.
CREATE INDEX ix_match_appeal_open ON v2.match_appeal (match_id) WHERE closed_at IS NULL;


-- Голос за отмену слова.
--
-- Заменяет jsonb appeal_votes: карту «слово → список проголосовавших» в
-- строке комнаты. Два одновременных голоса читали одну карту и записывали
-- две — второй затирал первого, и голос молча пропадал. Здесь голос — строка,
-- а повторный голос того же игрока отвергает первичный ключ.
--
-- Три колонки в ключе, а не две из §6.2, — следствие составного ключа
-- match_turn_word (см. выше).
CREATE TABLE v2.match_appeal_vote (
    turn_id         UUID        NOT NULL,
    word_id         VARCHAR(80) NOT NULL,
    voter_player_id UUID        NOT NULL,
    cast_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_match_appeal_vote PRIMARY KEY (turn_id, word_id, voter_player_id),
    CONSTRAINT fk_match_appeal_vote_word FOREIGN KEY (turn_id, word_id)
        REFERENCES v2.match_turn_word (turn_id, id) ON DELETE CASCADE
);

-- «Мой голос» в проекции окна апелляции.
CREATE INDEX ix_match_appeal_vote_voter ON v2.match_appeal_vote (voter_player_id);


-- Пауза: строка есть — пауза есть.
--
-- Сегодня это семь колонок строки комнаты (game_paused, host_paused,
-- pause_reason, pause_started_at_ms, paused_turn_remaining_ms,
-- paused_appeal_remaining_ms) плюс два jsonb со списками отсутствующих. Из-за
-- одной пары флагов «пауза хозяина» и «пауза по обрыву связи» затирали друг
-- друга: хозяин снимал свою паузу и снимал заодно чужую, а игра
-- продолжалась без половины игроков.
--
-- Ключ (match_id, kind) делает их независимыми: партия идёт, когда строк нет
-- ни одной.
CREATE TABLE v2.match_pause (
    match_id            UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    -- host | disconnect
    kind                VARCHAR(16) NOT NULL,
    turn_remaining_ms   INTEGER     NOT NULL DEFAULT 0,
    appeal_remaining_ms INTEGER     NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_by          UUID,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_match_pause PRIMARY KEY (match_id, kind),
    CONSTRAINT ck_match_pause_kind CHECK (kind IN ('host', 'disconnect')),
    CONSTRAINT ck_match_pause_remaining
        CHECK (turn_remaining_ms >= 0 AND appeal_remaining_ms >= 0)
);

-- Сторож технического поражения: «сколько уже висит пауза по обрыву».
CREATE INDEX ix_match_pause_disconnect ON v2.match_pause (started_at) WHERE kind = 'disconnect';


-- Кого не хватает в LiveKit прямо сейчас.
--
-- Заменяет пару jsonb pause_missing_uids + pause_missing_names, где имена были
-- второй копией чужих данных, а связь между двумя массивами держалась
-- порядком элементов.
CREATE TABLE v2.match_pause_absentee (
    match_id      UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    player_id     UUID        NOT NULL,
    missing_since TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_match_pause_absentee PRIMARY KEY (match_id, player_id)
);


-- ═══════════════════════════ sabotage ═══════════════════════════

-- Право играть с диверсиями.
--
-- Сегодня это две колонки карточки игрока (sabotage_unlimited,
-- sabotage_games_used) плюс константа FREE_SABOTAGE_GAMES = 5 в коде.
-- Предел вынесен в колонку: раздать десять партий одному человеку сегодня
-- нельзя иначе как выкаткой.
--
-- Внешний ключ на учётку есть: это право УЧЁТКИ, а не место за столом, и
-- бота здесь быть не может.
CREATE TABLE v2.sabotage_entitlement (
    player_id        UUID     NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    unlimited        BOOLEAN  NOT NULL DEFAULT FALSE,
    free_games_limit INTEGER  NOT NULL DEFAULT 5,
    -- Растёт атомарным UPDATE ... SET free_games_used = free_games_used + 1
    -- (§6.4): две вкладки, одновременно начавшие партию с диверсиями,
    -- потеряли бы один инкремент и подарили игроку бесплатную партию.
    free_games_used  INTEGER  NOT NULL DEFAULT 0,
    CONSTRAINT pk_sabotage_entitlement PRIMARY KEY (player_id),
    CONSTRAINT ck_sabotage_entitlement_counts
        CHECK (free_games_limit >= 0 AND free_games_used >= 0)
);


-- Журнал списаний бесплатных партий.
--
-- Заменяет sabotage_game_use, где ключом была склейка «roomId-gameNumber-uid»
-- в VARCHAR(200) (§6.3, «дубли ключа»). Здесь та же тройка — уникальный
-- индекс, и он же ключ идемпотентности: повторный старт той же партии не
-- спишет вторую бесплатную игру.
--
-- room_id БЕЗ внешнего ключа намеренно, хотя таблица комнат рядом: списание
-- обязано пережить уборку комнаты. Бесплатная партия израсходована, даже если
-- комнаты больше нет, и каскад молча вернул бы её игроку.
CREATE TABLE v2.sabotage_game_grant (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY,
    player_id   UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    room_id     VARCHAR(24) NOT NULL,
    game_number INTEGER     NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_sabotage_game_grant PRIMARY KEY (id),
    CONSTRAINT ux_sabotage_game_grant UNIQUE (player_id, room_id, game_number)
);

-- «Сколько партий я потратил и когда» — карточка расхода в профиле.
CREATE INDEX ix_sabotage_game_grant_player ON v2.sabotage_game_grant (player_id, consumed_at DESC);


-- Стартовая обойма учётки: ровно пять строк на игрока.
--
-- Сегодня это jsonb-массив default_meme_loadout в карточке игрока. Замена
-- одного мема переписывала весь массив, а «в скольких обоймах стоит этот
-- мем» — вопрос, на который массив не отвечал вовсе; при снятии мема с
-- публикации приходилось перебирать всех игроков.
--
-- meme_id без внешнего ключа: таблицы v2.meme ещё нет (кластер media). Индекс
-- по нему заведён заранее — он и есть тот ответ, ради которого мем нельзя
-- удалить молча.
CREATE TABLE v2.player_default_loadout (
    player_id  UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    slot_index INTEGER     NOT NULL,
    meme_id    UUID        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_player_default_loadout PRIMARY KEY (player_id, slot_index),
    CONSTRAINT ck_player_default_loadout_slot CHECK (slot_index BETWEEN 0 AND 4),
    -- Один и тот же мем дважды в обойме — это четыре заряда вместо пяти.
    CONSTRAINT ux_player_default_loadout_meme UNIQUE (player_id, meme_id)
);

CREATE INDEX ix_player_default_loadout_meme ON v2.player_default_loadout (meme_id);


-- Диверсант партии: перезарядка и курсор выдачи мемов.
--
-- Строка есть у каждого, кто может стрелять, — ВКЛЮЧАЯ ТЕСТ-БОТА. Это и есть
-- ответ на вопрос «где место восемнадцати колонок test_bot* и jsonb
-- test_bot_runtime»: у бота больше нет отдельного мира внутри строки комнаты,
-- он получает те же строки, что и человек, и отличается одним признаком.
-- Сегодня GameRules.memeQueue написан дважды — для RoomPlayer и для карты
-- runtime, — и две реализации одного правила уже разошлись.
--
-- Эта строка — цель SELECT ... FOR UPDATE на выстреле (§6.4): под ней
-- проверяется перезарядка и лимит трёх клипов.
CREATE TABLE v2.match_saboteur (
    match_id          UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    player_id         UUID        NOT NULL,
    -- Пусто — перезарядки нет. Сегодня это bigint со значением 0, который
    -- невозможно отличить от «перезарядка кончилась в 1970 году».
    cooldown_until    TIMESTAMPTZ,
    -- Куда встал круг выдачи мемов, когда кончились и резерв, и переработка.
    meme_cycle_cursor INTEGER     NOT NULL DEFAULT 0,
    is_bot            BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_match_saboteur PRIMARY KEY (match_id, player_id),
    CONSTRAINT ck_match_saboteur_cursor CHECK (meme_cycle_cursor >= 0)
);


-- Боезапас: строка на вид патрона.
--
-- Заменяет jsonb arsenal — карту «вид → количество» в строке места игрока.
-- Списание становится одним оператором:
--   UPDATE ... SET amount = amount - 1 WHERE ... AND amount > 0
-- Ноль изменённых строк — это NO_AMMO. Сегодня проверка и списание разнесены
-- (прочитали карту, посчитали, записали карту целиком), и два быстрых
-- выстрела списывают один патрон.
--
-- CHECK'а на набор видов патрона здесь НЕТ намеренно, и это прямое следствие
-- §6.3: источник правды об оружии — WeaponRegistry в коде, вторая копия
-- набора в базе неминуемо с ним разойдётся. Один раз это уже произошло —
-- ARSENAL_KEYS разошёлся с BASE_ARSENAL, и GameRules.arsenal() падал с NPE.
CREATE TABLE v2.match_player_ammo (
    match_id  UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    player_id UUID        NOT NULL,
    ammo_type VARCHAR(16) NOT NULL,
    amount    INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_match_player_ammo PRIMARY KEY (match_id, player_id, ammo_type),
    CONSTRAINT ck_match_player_ammo_amount CHECK (amount >= 0)
);


-- Обойма партии: пять слотов и круг, по которому они ходят.
--
-- Заменяет ПЯТЬ jsonb-массивов места игрока сразу: meme_loadout,
-- meme_available_ids, meme_reserve_ids, meme_recycle_queue, used_meme_ids.
-- Все пять описывают одни и те же пять мемов, поэтому один мем лежал в
-- документе до пяти раз, а согласовывать списки приходилось руками
-- (GameRules.filterToLoadout выбрасывал из очередей то, чего нет в обойме, —
-- то есть чинил расхождение на каждом чтении).
--
-- Здесь мем — одна строка. bucket говорит, в какой части круга он сейчас:
-- available (готов к выстрелу) → recycle (потрачен) → available снова, когда
-- кончится reserve. Уникальность (match, player, meme) и делает пять списков
-- разбиением, а не пятью копиями.
--
-- Списка used здесь нет: «какие мемы я уже показал» — это строки
-- sabotage_event с weapon_type='meme', и отдельный массив был его копией.
CREATE TABLE v2.match_loadout_slot (
    match_id     UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    player_id    UUID        NOT NULL,
    slot_index   INTEGER     NOT NULL,
    meme_id      UUID        NOT NULL,
    -- available | reserve | recycle
    bucket       VARCHAR(16) NOT NULL,
    bucket_order INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_match_loadout_slot PRIMARY KEY (match_id, player_id, slot_index),
    CONSTRAINT ck_match_loadout_slot_index CHECK (slot_index BETWEEN 0 AND 4),
    CONSTRAINT ck_match_loadout_slot_bucket CHECK (bucket IN ('available', 'reserve', 'recycle')),
    CONSTRAINT ux_match_loadout_slot_meme UNIQUE (match_id, player_id, meme_id)
);

-- «Чем я могу выстрелить прямо сейчас» и «что выдать следующим» — оба вопроса
-- одним проходом по одному индексу.
CREATE INDEX ix_match_loadout_slot_bucket
    ON v2.match_loadout_slot (match_id, player_id, bucket, bucket_order);


-- Прогресс команды к редким диверсиям.
--
-- Заменяет пару jsonb special_reward_progress_by_team и
-- special_reward_cursor_by_team — две карты «команда → число» в строке
-- комнаты, которые обязаны были меняться вместе и потому расходились.
--
-- guessed_total растёт атомарным UPDATE (§6.4). recipient_cursor нужен, чтобы
-- редкие награды не копились у одного игрока команды.
CREATE TABLE v2.match_reward_progress (
    match_id         UUID     NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    room_team_id     UUID     NOT NULL REFERENCES v2.room_team (id) ON DELETE CASCADE,
    guessed_total    INTEGER  NOT NULL DEFAULT 0,
    recipient_cursor INTEGER  NOT NULL DEFAULT 0,
    CONSTRAINT pk_match_reward_progress PRIMARY KEY (match_id, room_team_id),
    CONSTRAINT ck_match_reward_progress_counts
        CHECK (guessed_total >= 0 AND recipient_cursor >= 0)
);


-- Занятость канала эффекта.
--
-- Заменяет jsonb sabotage_locks: объект с полями videoUntil, voiceUntil,
-- crocodileUntil, overlayUntil, replacementUntil, replacementTurnId и вложенной
-- картой replacementRecordingByAttacker. Два выстрела по РАЗНЫМ каналам
-- переписывали один и тот же объект целиком, и один из них терялся — то есть
-- эффект применялся, а канал оставался свободным.
--
-- Здесь канал — строка, и она же цель SELECT ... FOR UPDATE. Порядок взятия
-- каналов внутри партии — по алфавиту (§6.4), иначе два выстрела по разным
-- каналам дают клинч.
--
-- Шестого канала (NONE из EffectLock) здесь нет: помидор, мем и пук ничего на
-- сцене не занимают, и строки им не нужно.
--
-- replacementRecordingByAttacker сюда НЕ переехал: занятость съёмки — это
-- срок в строке самого клипа (replacement_clip.record_deadline), потому что
-- она поимённая, а не общая, — десять игроков вправе снимать одновременно.
CREATE TABLE v2.match_effect_lock (
    match_id     UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    -- video | voice | crocodile | overlay | replacement
    channel      VARCHAR(16) NOT NULL,
    locked_until TIMESTAMPTZ NOT NULL,
    -- Ход, в котором канал занят: по нему Подмена узнаёт «этот клип снят в
    -- текущем ходу, показывать его нельзя».
    turn_id      UUID        REFERENCES v2.match_turn (id) ON DELETE SET NULL,
    CONSTRAINT pk_match_effect_lock PRIMARY KEY (match_id, channel),
    CONSTRAINT ck_match_effect_lock_channel
        CHECK (channel IN ('video', 'voice', 'crocodile', 'overlay', 'replacement'))
);


-- Журнал применённых диверсий.
--
-- Заменяет сразу два jsonb: sabotage_event (последний выстрел) и
-- sabotage_events_recent (массив последних 24). Одно и то же событие лежало в
-- двух местах, и «последнее» умело не совпасть с последним элементом массива.
-- Здесь и то и другое — один отбор по (match_id, seq DESC) с разным пределом.
--
-- Из события выброшены шесть колонок каталога мемов (title, src, poster,
-- media_path, poster_path, storage_provider): §6.3 числит их копиями чужих
-- данных, и они устаревали в тот же миг, когда владелец переливал ролик.
-- Осталась ссылка meme_id, каталог читается через MemeCatalogPort.
CREATE TABLE v2.sabotage_event (
    id                 UUID        NOT NULL,
    match_id           UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    -- Номер выстрела в партии. Он же порядок: часы клиентов для этого не
    -- годятся, а два выстрела в одну миллисекунду бывают.
    seq                INTEGER     NOT NULL,
    turn_id            UUID        REFERENCES v2.match_turn (id) ON DELETE SET NULL,
    -- Код оружия из WeaponType. CHECK'а нет по той же причине, что у вида
    -- патрона: каталог живёт в WeaponRegistry.
    weapon_type        VARCHAR(16) NOT NULL,
    attacker_player_id UUID        NOT NULL,
    -- Пусто у оружия, которое бьёт по сцене, а не по человеку.
    target_player_id   UUID,
    meme_id            UUID,
    clip_id            UUID,
    duration_ms        INTEGER     NOT NULL DEFAULT 0,
    -- Куда прилетел помидор: доли ширины и высоты сцены, а не пиксели, —
    -- у зрителей разные экраны.
    pos_x              DOUBLE PRECISION,
    pos_y              DOUBLE PRECISION,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_sabotage_event PRIMARY KEY (id),
    CONSTRAINT ux_sabotage_event_seq UNIQUE (match_id, seq),
    CONSTRAINT ck_sabotage_event_duration CHECK (duration_ms >= 0),
    CONSTRAINT ck_sabotage_event_position
        CHECK ((pos_x IS NULL) = (pos_y IS NULL)
           AND (pos_x IS NULL OR (pos_x BETWEEN 0 AND 1 AND pos_y BETWEEN 0 AND 1)))
);
-- Отдельного индекса (match_id, seq DESC) нет: ux_sabotage_event_seq отвечает
-- и на «последний выстрел», и на «последние 24».


-- Слот подменного клипа.
--
-- Заменяет jsonb replacement_recordings — карту «id клипа → объект» в строке
-- комнаты. Съёмка идёт секунд десять, и всё это время карта переписывалась
-- вместе с комнатой; два игрока, снимавших одновременно, теряли один клип.
--
-- state, а не пара флагов: §6.4 требует условных переходов
-- (UPDATE ... WHERE state='recording'), потому что @Version не умеет отличить
-- «уже готов» от «уже выброшен» — оба выглядят как устаревшая версия.
--
-- record_deadline — срок, после которого начатая и брошенная съёмка считается
-- потерянной (вкладку закрыли, итог не пришёл). Сегодня это константа
-- ABANDONED_AFTER_MS в коде и вычитание дат при каждом чтении.
CREATE TABLE v2.replacement_clip (
    id                 UUID        NOT NULL,
    match_id           UUID        NOT NULL REFERENCES v2.match (id) ON DELETE CASCADE,
    attacker_player_id UUID        NOT NULL,
    target_player_id   UUID        NOT NULL,
    -- Ход, в котором клип снят: применить его в том же ходу нельзя — зрители
    -- только что видели этот кусок вживую.
    recorded_turn_id   UUID        NOT NULL REFERENCES v2.match_turn (id) ON DELETE CASCADE,
    -- recording | ready | consumed | discarded
    state              VARCHAR(16) NOT NULL DEFAULT 'recording',
    record_deadline    TIMESTAMPTZ NOT NULL,
    ready_at           TIMESTAMPTZ,
    -- Выстрел, в котором клип показали. SET NULL: журнал выстрелов может уйти
    -- вместе с партией раньше, чем уборка дойдёт до слотов.
    consumed_event_id  UUID        REFERENCES v2.sabotage_event (id) ON DELETE SET NULL,
    discarded_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_replacement_clip PRIMARY KEY (id),
    CONSTRAINT ck_replacement_clip_state
        CHECK (state IN ('recording', 'ready', 'consumed', 'discarded')),
    CONSTRAINT ck_replacement_clip_ready
        CHECK ((state IN ('ready', 'consumed')) = (ready_at IS NOT NULL)),
    CONSTRAINT ck_replacement_clip_consumed
        CHECK ((state = 'consumed') = (consumed_event_id IS NOT NULL)),
    CONSTRAINT ck_replacement_clip_discarded
        CHECK ((state = 'discarded') = (discarded_at IS NOT NULL))
);

-- Лимит трёх живых клипов на стрелка — счёт по этому индексу под блокировкой
-- match_saboteur.
CREATE INDEX ix_replacement_clip_live
    ON v2.replacement_clip (match_id, attacker_player_id) WHERE state IN ('recording', 'ready');
-- Сторож брошенных съёмок.
CREATE INDEX ix_replacement_clip_deadline
    ON v2.replacement_clip (record_deadline) WHERE state = 'recording';


-- ═══════════════ Что стало с двадцатью восемью jsonb ═══════════════
--
-- Ни одного jsonb в кластере не осталось. Разбор по колонкам сегодняшней
-- room и room_player:
--
--   bag                              → v2.match_word (строка на слово, позиция в колоде)
--   turn_guessed_words               → v2.match_turn_word
--   appeal_votes                     → v2.match_appeal_vote
--   team_order                       → v2.room_team.slot и v2.match_team.turn_order
--   team_rosters                     → v2.match_player
--   game_player_names_by_uid         → v2.match_player.display_name
--   last_turn                        → выводится из match_turn и match_turn_word
--   technical_termination            → колонки v2.match + v2.match_pause_absentee
--   pause_missing_uids/names         → v2.match_pause_absentee
--   sabotage_event                   → последняя строка v2.sabotage_event
--   sabotage_events_recent           → её же 24 строки
--   sabotage_locks                   → v2.match_effect_lock (+ record_deadline клипа)
--   replacement_recordings           → v2.replacement_clip
--   special_reward_progress_by_team  → v2.match_reward_progress.guessed_total
--   special_reward_cursor_by_team    → v2.match_reward_progress.recipient_cursor
--   arsenal (room_player)            → v2.match_player_ammo
--   meme_loadout, meme_available_ids,
--   meme_reserve_ids,
--   meme_recycle_queue               → v2.match_loadout_slot (bucket)
--   used_meme_ids                    → выводится из v2.sabotage_event
--   test_bot_ids                     → v2.room_player.bot + таблицы прогона (admin)
--   test_bot_runtime                 → строки диверсий, общие с людьми (см. ниже)
--   matchmaking_uids/names/team_ids,
--   ranked_team_slots/assignments    → кластер lobby (matchmaking_ticket*)
--   words (room_word_submission)     → v2.word_submission
--
-- ─────────────────── Семнадцать колонок про тест-ботов ───────────────────
--
-- Пересчитано по V1__init_schema.sql, а не на глаз. В room их тринадцать:
-- is_test_room, test_owner_uid, test_bot_next_action_at,
-- test_bot_next_sabotage_at, test_bot_next_chat_at, test_bot_special_effect_until,
-- test_bot_special_effect_cursor, test_bot_voice_effect_until,
-- test_bot_voice_effect_cursor, test_owner_explainer_game_number,
-- test_owner_explainer_turn_number, test_bot_ids, test_bot_runtime. Ещё четыре
-- разбросаны по соседям: room_player.is_test_bot и test_bot_index,
-- room_chat_message.is_test_bot, room_word_submission.is_test_bot_submission.
-- Итого семнадцать, и расходятся они по трём адресам:
--
-- 1. ПРИЗНАК ОСТАЁТСЯ ЗДЕСЬ, ОДНОЙ КОЛОНКОЙ НА СУЩНОСТЬ: room.kind='test',
--    room_player.bot, match_player.bot, match_saboteur.is_bot,
--    room_chat_message.author_seat='bot'. Признак «это бот» нужен правилам
--    партии (тест-ботам не выдаются редкие награды, RewardRules), поэтому
--    выносить его из кластера нельзя.
--
-- 2. СОСТОЯНИЕ БОТА КАК ИГРОКА — ТЕ ЖЕ СТРОКИ, ЧТО У ЧЕЛОВЕКА. Одиннадцать
--    ключей jsonb test_bot_runtime (name, memeLoadout, memeMetaById,
--    usedMemeIds, memeAvailableIds, memeReserveIds, memeRecycleQueue,
--    memeCycleCursor, arsenal, sabotageCooldownUntil, testBotLastShotAt)
--    разошлись так: name → match_player.display_name; arsenal →
--    match_player_ammo; четыре списка мемов → match_loadout_slot; курсор и
--    перезарядка → match_saboteur; usedMemeIds выводится; memeMetaById — это
--    шестая копия каталога мемов, и её убирает §6.3. Выжил один ключ,
--    testBotLastShotAt, и он уезжает в пункт 3. Именно ради этого у бота
--    появляется синтетический uuid: чтобы его строки ничем не отличались от
--    строк человека и правило не приходилось писать дважды.
--
-- 3. РАСПИСАНИЕ И ХОЗЯИН ПРОГОНА — КЛАСТЕР ADMIN, НЕ ЭТОТ. test_owner_uid,
--    три колонки next_*_at, два счётчика special_effect и testBotLastShotAt
--    описывают не комнату, а ПРОГОН ботов: у него свой владелец (владелец
--    сервиса), свой жизненный цикл и свой писатель — планировщик. По §6.2 их
--    место в test_bot_run / test_bot / test_bot_schedule, и заводит их
--    миграция кластера admin. Здесь их нет намеренно: колонка расписания в
--    строке комнаты — это тот же тик раз в секунду поверх ста колонок, от
--    которого этот файл и уходит.
--    Мёртвые test_bot_voice_effect_until/cursor (§6.3) не переезжают никуда.
--
-- ─────────────────── Чего в кластере нет намеренно ───────────────────
--
--   room_recording_policy   — владелец recording, заводится его миграцией;
--                             колонки record_game и recording_preference_*
--                             из room туда и уезжают (§6.1).
--   matchmaking_*           — владелец lobby.
--   room_player.clock_probe_at — костыль калибровки часов, заменён
--                             GET /api/v2/game/clock (§6.3).
--   room.video_provider, matchmaking_min_players,
--   room.sabotage_cooldown_until (везде 0) — мёртвые (§6.3).
--   room.word_count, words_left, word_revision, current_turn_score,
--   room_team.score, current_team_index, explainer_name, guesser_name,
--   room_team.member_uids — выводимые; денормализованных счётчиков в кластере
--                             нет, расходиться нечему.
