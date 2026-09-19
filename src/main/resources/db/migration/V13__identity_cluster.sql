-- Кластер личности по §6 плана v2: учётка и сессии (auth), карточка игрока и
-- согласия (profile), блокировки и след модератора (admin). Одиннадцать таблиц
-- вместо одной app_user на 36 колонок.
--
-- Номер V13, а не V12: V12 занята мостом идентификаторов (legacy_id_bridge).
--
-- ─────────────────── Почему app_user разрезана именно так ───────────────────
-- Сегодня в одной строке лежат вещи с разной частотой записи и разными
-- писателями, и каждая запись переписывает строку целиком:
--
--   token_version, password_hash   меняются считаными разами за жизнь учётки,
--                                  писатель — auth и бан администратора;
--   last_seen_at, active_room_id   меняются каждые 30–60 секунд у КАЖДОГО
--                                  игрока, писатель — profile;
--   legal_*, adult_confirmed       пишутся один раз, при входе в игру;
--   nickname, division, avatar     меняются руками игрока, изредка;
--   banned                         пишет администратор.
--
-- Пока это одна строка, отметка присутствия и смена пароля дерутся за одну
-- версию строки, а аватарка на 120 000 символов лежит в той же странице, что
-- и счётчик отзыва токенов, — и переписывается вместе с ним шесть раз в
-- минуту. Разрезание по владельцу и по частоте записи убирает и то и другое:
-- горячий player_presence живёт своей таблицей с fillfactor 70 (обновление
-- на месте, без раздувания), холодные секреты — своей.
--
-- ─────────────────── Почему схема v2 ───────────────────
-- Та же причина, что в V11: пять целевых имён заняты сегодняшними таблицами в
-- public. Здесь занято ещё три — refresh_token, password_reset_token,
-- user_ban, — и legal_consent, и они продолжают работать: на них живут
-- /api/auth и /api/portal. Схема снимается одним ALTER TABLE ... SET SCHEMA
-- в день, когда старые таблицы уйдут.
--
-- ─────────────────── Почему нет FK из таблиц V11 на user_account ───────────────────
-- Области friends, chat, team и rating уже пишут в свои таблицы v2, но
-- игрока они кладут как md5('player:' || uid) — мост V12, — и строки в
-- user_account у него нет и не будет, пока auth не переедет. Поставить сюда
-- внешний ключ значило бы сломать сегодняшние заявки в друзья и переписку.
-- Внутри кластера личности внешние ключи расставлены все — кроме одного
-- разряда колонок, и вот почему.
--
-- ─────────────────── Почему у авторства нет внешнего ключа ───────────────────
-- Колонки «кто это сделал» — granted_by, banned_by, lifted_by, decided_by,
-- actor_player_id — намеренно оставлены обычными UUID. Внешний ключ на них
-- даёт выбор из двух зол: RESTRICT превращает уборку брошенных гостевых
-- учёток в скан журнала модерации на каждую удаляемую строку (проверка ссылки
-- без индекса — это seq scan), а SET NULL молча стирает автора решения, ради
-- которого журнал и ведётся. Заводить индекс только ради проверки ссылки —
-- третье зло: он не отвечает ни на один вопрос консоли.
--
-- Колонки «над кем это сделано» (player_id везде) внешний ключ имеют, каскад
-- на удаление учётки и покрывающий индекс — тоже: удаление учётки обязано
-- уносить её сессии, согласия и баны, а не оставлять их сиротами.
--
-- Данные не переносятся: старые таблицы остаются как есть, новые пусты.


-- ═══════════════════════════ auth ═══════════════════════════

-- Чем игрок доказывает, что он это он. От прежней app_user здесь остались
-- шесть колонок из тридцати шести: всё остальное — не про личность.
--
-- kind вместо флага guest: гость и участник — два состояния одной учётки, и
-- апгрейд гостя в §6.4 плана записан условным UPDATE ... WHERE kind='guest'.
-- Булев флаг такого перехода выразить не мог: два одновременных апгрейда
-- проходили оба (дыра A4), потому что проверка «он ещё гость?» была отдельным
-- чтением.
--
-- banned здесь НЕТ: факт блокировки — строка v2.user_ban, а мгновенный отзыв
-- доступа даёт token_version. Сегодня это три источника правды на один вопрос
-- (флаг, строка причины и поколение токенов), и они умеют разойтись.
CREATE TABLE v2.user_account (
    player_id           UUID         NOT NULL,
    kind                VARCHAR(8)   NOT NULL,
    -- Регистр не хранится нормализованным: адрес показывается человеку так,
    -- как он его ввёл, а сравнивается без регистра — уникальным индексом ниже.
    email               VARCHAR(320),
    password_hash       VARCHAR(100),
    -- Растёт при бане, смене пароля и выходе со всех устройств. §6.4 плана
    -- требует атомарного UPDATE ... SET token_version = token_version + 1:
    -- сегодня это read-modify-write в двух местах сразу (AuthServiceImpl и
    -- AdminServiceImpl), и одновременные бан и смена пароля теряют один
    -- инкремент — то есть отозванная сессия остаётся живой.
    token_version       INTEGER      NOT NULL DEFAULT 0,
    -- Когда учётка стала полноценной. Это и есть registeredAtMs контракта
    -- (MyAccountResponseDTO): у гостя даты регистрации нет по определению.
    member_since        TIMESTAMPTZ,
    -- §6.3 плана числит эту колонку мёртвой по старому коду, но в v2 её
    -- читает MyAccountResponseDTO.passwordUpdatedAtMs — DTO уже в
    -- спецификации, и форму ответа менять нельзя. Поэтому колонка живёт.
    password_updated_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_account PRIMARY KEY (player_id),
    CONSTRAINT ck_user_account_kind CHECK (kind IN ('guest', 'member')),
    -- «Участник» и «есть дата вступления» — одно и то же событие. Разойтись
    -- им нечем: апгрейд ставит обе колонки одним UPDATE.
    CONSTRAINT ck_user_account_member CHECK ((kind = 'member') = (member_since IS NOT NULL)),
    -- У гостя нет ни почты, ни пароля: он входит по одному лишь токену.
    -- Ограничение держит апгрейд честным — нельзя завести гостя с паролем и
    -- потом гадать, кем он был.
    CONSTRAINT ck_user_account_guest_has_no_secrets
        CHECK (kind = 'member' OR (email IS NULL AND password_hash IS NULL)),
    CONSTRAINT ck_user_account_member_has_email CHECK (kind = 'guest' OR email IS NOT NULL)
);

-- Почта занята — это уникальный индекс, а не чтение перед вставкой (§6.4).
-- Регистр не важен: сегодня вход ищет findFirstByEmailIgnoreCase, и без
-- lower() «Вася@» и «вася@» стали бы двумя учётками с одним владельцем.
-- Частичный, потому что у гостей почты нет и NULL-ы уникальности не мешают,
-- но и в индекс их класть незачем — гостей заведомо больше.
CREATE UNIQUE INDEX ux_user_account_email
    ON v2.user_account (lower(email)) WHERE email IS NOT NULL;

-- Реестр владельца сервиса (GET /api/v2/admin/users) листает участников по
-- дате вступления вниз и гостей не показывает вовсе. Сегодня этот список
-- берётся целиком и сортируется в памяти (ListRegisteredUsersUseCase), потому
-- что сортировать в базе было не по чему: registered_at не индексирован.
CREATE INDEX ix_user_account_members
    ON v2.user_account (member_since DESC) WHERE kind = 'member';

-- Уборка брошенных гостевых учёток (maintenance): «кто заведён раньше чем».
CREATE INDEX ix_user_account_guests
    ON v2.user_account (created_at) WHERE kind = 'guest';


-- Кто администратор и кто владелец.
--
-- Сегодня ответ на этот вопрос лежит в настройках приложения: списки
-- admin-uids и admin-emails плюс owner-email (PrincipalResolver.toPrincipal).
-- Права, заданные почтой, означают, что смена почты меняет права, а опечатка
-- в списке раздаёт их молча. Здесь право — строка, у неё есть выдавший и
-- дата, и снять её можно, не перезапуская сервис.
CREATE TABLE v2.account_role (
    player_id  UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL,
    -- Пусто у самого первого владельца: его некому было назначить.
    -- Без внешнего ключа — это авторство (см. шапку файла).
    granted_by UUID,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_account_role PRIMARY KEY (player_id, role),
    CONSTRAINT ck_account_role_role CHECK (role IN ('ADMIN', 'OWNER'))
);

-- Владелец у сервиса ровно один: сегодня это единственная строка настройки
-- owner-email, и второго владельца завести было физически нельзя. Частичный
-- уникальный индекс сохраняет это свойство, не мешая множеству администраторов.
CREATE UNIQUE INDEX ux_account_role_single_owner
    ON v2.account_role (role) WHERE role = 'OWNER';


-- Долгоживущая половина пары токенов, она же список сессий.
--
-- token_hash — BYTEA, а не VARCHAR(64): это SHA-256, тридцать два байта.
-- Шестнадцатеричная запись стоила вдвое больше места в таблице и в индексе и
-- не давала ничего — по хешу никто не ищет глазами.
--
-- family_id — цепочка ротаций одного входа. Без неё реюз украденного токена
-- (A11) нечем погасить: сегодня отзывается ровно предъявленная строка, а вор
-- продолжает крутить свою ветку цепочки. С family_id гашение — один UPDATE
-- по всей семье.
CREATE TABLE v2.refresh_token (
    token_hash     BYTEA        NOT NULL,
    player_id      UUID         NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    family_id      UUID         NOT NULL,
    -- На кого этот токен обменяли при ротации. Водяной знак без внешнего
    -- ключа — как last_read_message_id в переписке: он обязан пережить
    -- уборку истёкшей строки, на которую показывает, иначе одно удаление
    -- рвало бы цепочку посередине. Плюс ссылка на себя же заставила бы
    -- каждое удаление в token-sweeps искать своих детей — по неиндексированной
    -- колонке, то есть сканом всей таблицы.
    replaced_by    BYTEA,
    issued_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    -- rotated | logout | logout_all | ban | reuse | password_change.
    -- Набор намеренно не закрыт CHECK: это причина в журнале, а не состояние,
    -- и новый повод отзыва не должен требовать миграции.
    revoked_reason VARCHAR(24),
    created_ip     VARCHAR(45),
    user_agent     VARCHAR(200),
    CONSTRAINT pk_refresh_token PRIMARY KEY (token_hash),
    CONSTRAINT ck_refresh_token_revoked CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

-- Гашение семьи при реюзе и проверка «жива ли ещё цепочка». Частичный, потому
-- что отозванные строки в этом вопросе не участвуют, а их со временем
-- большинство: каждая ротация оставляет позади ещё одну.
CREATE INDEX ix_refresh_token_family
    ON v2.refresh_token (family_id) WHERE revoked_at IS NULL;

-- «Выйти со всех устройств» и список своих сессий — один и тот же отбор по
-- игроку. Индекс полный, а не частичный «где не отозван», хотя отбор всегда
-- идёт по живым: тот же индекс обязан обслуживать каскадное удаление учётки,
-- а оно смотрит на все строки игрока, включая отозванные. Частичный индекс
-- каскад не покрыл бы, и уборка гостевых учёток шла бы сканом.
CREATE INDEX ix_refresh_token_player ON v2.refresh_token (player_id);

-- Уборка истёкших (maintenance/token-sweeps).
CREATE INDEX ix_refresh_token_expires ON v2.refresh_token (expires_at);


-- Одноразовая ссылка восстановления пароля. Тоже хеш и тоже BYTEA.
--
-- Использование и обесценивание — разные события, и разделены они не ради
-- красоты: «ссылку уже применили» и «её отменил новый запрос» отличаются для
-- человека, который жмёт на письмо недельной давности.
CREATE TABLE v2.password_reset_token (
    token_hash     BYTEA       NOT NULL,
    player_id      UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    requested_ip   VARCHAR(45),
    CONSTRAINT pk_password_reset_token PRIMARY KEY (token_hash),
    -- Применить обесцененную ссылку нельзя, обесценить применённую — незачем.
    CONSTRAINT ck_password_reset_token_final CHECK (used_at IS NULL OR invalidated_at IS NULL)
);

-- Новый запрос гасит прежние ссылки игрока и показывает, когда он просил в
-- прошлый раз (защита от рассылки писем по кругу).
CREATE INDEX ix_password_reset_token_player
    ON v2.password_reset_token (player_id, created_at DESC);

-- Уборка: истёкшие среди тех, что ещё чего-то ждут.
CREATE INDEX ix_password_reset_token_expires
    ON v2.password_reset_token (expires_at) WHERE used_at IS NULL AND invalidated_at IS NULL;


-- ═══════════════════════════ profile ═══════════════════════════

-- Карточка игрока: имя, язык, дивизион.
--
-- displayName нет: §3.2 плана слил его с nickname. Сегодня их пишут одинаково
-- во всех шести местах, а отдельно displayName меняет ровно один метод — и
-- порождает состояние «ник Vasya, имя Петя», которое потом лечит resolvedNickname.
--
-- Таблицы nickname_index тоже нет: уникальность даёт ux_player_profile_nickname
-- ниже. Вместе с ней исчезают починка индексов (57 строк ProfileServiceImpl),
-- фолбэки getByNicknameKey/getNicknamesOfUid и находка B9 — merge по
-- присвоенному @Id, переписывавший чужую строку.
--
-- Строка есть и у гостя: ник Guest###### должен где-то лежать, а согласия
-- гость даёт наравне с участником.
CREATE TABLE v2.player_profile (
    player_id           UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    -- 20, а не 40: ровно столько разрешает Ids.NICKNAME, и столько же обещает
    -- контракт («латиница, 3–20 знаков»).
    nickname            VARCHAR(20) NOT NULL,
    -- Тот же ключ, что считает Ids.key (trim + lower), только считает его
    -- база. Поэтому «занято ли имя» и «под каким ключом лежит имя» больше не
    -- могут разойтись, и чинить индекс ников нечем и незачем.
    nickname_key        VARCHAR(20) GENERATED ALWAYS AS (lower(btrim(nickname))) STORED,
    division_language   VARCHAR(8)  NOT NULL DEFAULT 'ru',
    ui_language         VARCHAR(8)  NOT NULL DEFAULT 'ru',
    -- Дивизион выбирается один раз. §6.4 делает повтор условным UPDATE
    -- ... WHERE division_locked_at IS NULL: ноль изменённых строк = 409
    -- DIVISION_LOCKED. Сегодня это правило живёт в двух местах кода сразу.
    division_locked_at  TIMESTAMPTZ,
    -- Подтверждение совершеннолетия — факт о человеке, а не о документе, и
    -- RecordConsentRequestDTO прямо говорит, что это отдельное поле, а не
    -- седьмая «версия». Поэтому оно здесь, а не строкой в журнале согласий:
    -- версии у него нет и принять его повторно нельзя.
    adult_confirmed_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_player_profile PRIMARY KEY (player_id),
    -- Тот же шаблон, что проверяет @Nickname на входе. Здесь он стоит не
    -- вторым забором, а последним: ник приезжает ещё и из онбординга, и из
    -- генератора гостевых имён, и разойтись этим трём путям нечем.
    CONSTRAINT ck_player_profile_nickname CHECK (nickname ~ '^[A-Za-z][A-Za-z0-9_]{2,19}$')
);

-- Ник занят — уникальный индекс, а не чтение перед вставкой (§6.4). Он же
-- отвечает на GET /auth/accounts/nickname-availability.
--
-- Списка девяти языков в CHECK намеренно нет: §6.3 плана оставляет справочник
-- дивизионов в коде и в файлах public/i18n. Копия набора в базе разошлась бы
-- с ними на первом же добавленном языке, и добавление стоило бы миграции.
CREATE UNIQUE INDEX ux_player_profile_nickname ON v2.player_profile (nickname_key);


-- Аватар отдельной строкой.
--
-- Сегодня это колонка avatar_data_url в карточке игрока — до 120 000 символов
-- прямо в той строке, которую переписывает отметка присутствия. Из-за этого
-- же аватар тиражируется в места комнат, в состав зрителей и в таблицы
-- сезона (§6.3, «копии чужих данных»).
--
-- Хранится двоичным телом, а не data-URL: base64 стоит треть лишнего объёма,
-- а строка ответа собирается обратно из media_type и bytes — контракт
-- (avatarDataUrl) от этого не меняется.
CREATE TABLE v2.player_avatar (
    player_id  UUID        NOT NULL REFERENCES v2.player_profile (player_id) ON DELETE CASCADE,
    media_type VARCHAR(32) NOT NULL,
    bytes      BYTEA       NOT NULL,
    -- ETag ответа: браузер не тянет мегабайты аватаров на каждом открытии
    -- списка друзей. Считается при записи, поэтому расходиться не с чем.
    sha256     BYTEA       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_player_avatar PRIMARY KEY (player_id),
    -- Ровно те три типа, что разрешает шаблон ReplaceAvatarRequestDTO.
    CONSTRAINT ck_player_avatar_type CHECK (media_type IN ('image/webp', 'image/jpeg', 'image/png')),
    -- 92 160 байт = 90 КБ: столько двоичных данных даёт предел в 120 000
    -- символов base64, который сегодня стоит на входе.
    CONSTRAINT ck_player_avatar_size CHECK (octet_length(bytes) BETWEEN 1 AND 92160),
    CONSTRAINT ck_player_avatar_sha  CHECK (octet_length(sha256) = 32)
);
-- Индексов, кроме первичного ключа, нет: аватар читают только по игроку.


-- Где игрок сейчас и когда его видели. Самая горячая таблица кластера.
--
-- Пинг приходит раз в 30–60 секунд от каждого, кто открыл портал. Пока это
-- была колонка app_user.last_seen_at, каждый такой пинг переписывал строку с
-- паролем, согласиями и аватаркой целиком.
--
-- @Version здесь намеренно нет (§6.4): побеждает последняя запись, и это
-- верная семантика — «видели в 12:00:30» после «видели в 12:00:00» не
-- конфликт, а обновление.
--
-- fillfactor 70 оставляет в странице место под новые версии строк, чтобы
-- обновление проходило внутри той же страницы (HOT-update) и не трогало
-- индексы. Без него таблица, которую переписывают целиком каждую минуту,
-- растёт быстрее, чем автовакуум успевает её подчищать.
CREATE TABLE v2.player_presence (
    player_id       UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Комната, в которую игрока возвращает восстановление сессии. Внешнего
    -- ключа нет: таблица room новой схемы ещё не существует, а ссылаться на
    -- сегодняшнюю нельзя — у неё другой владелец.
    active_room_id  VARCHAR(24),
    active_room_at  TIMESTAMPTZ,
    CONSTRAINT pk_player_presence PRIMARY KEY (player_id),
    CONSTRAINT ck_player_presence_room CHECK (active_room_id ~ '^hat-[0-9a-f]{16}$'),
    -- «Игрок в комнате» и «когда он туда попал» появляются вместе: без даты
    -- нечем отличить свежий вход от строки, забытой с прошлой недели.
    CONSTRAINT ck_player_presence_room_at CHECK ((active_room_id IS NULL) = (active_room_at IS NULL))
) WITH (fillfactor = 70);

-- «Сколько игроков сейчас в портале» — счёт по last_seen_at за последние две
-- минуты (CountOnlinePlayersUseCase). Сегодня это seq scan по всей app_user.
CREATE INDEX ix_player_presence_seen ON v2.player_presence (last_seen_at DESC);


-- Журнал принятых документов: строка на документ, а не карта версий.
--
-- Сегодня это jsonb legal_versions в карточке игрока плюс дублирующая его
-- таблица legal_consent с тем же jsonb внутри. Принятие второй версии
-- соглашения перезаписывало первую, и «что именно человек принял в марте»
-- ответить было нельзя — а это ровно тот вопрос, ради которого согласие
-- вообще хранят.
CREATE TABLE v2.legal_consent (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,
    player_id        UUID         NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    document         VARCHAR(16)  NOT NULL,
    -- Дата выпуска документа строкой — так её проставляет фронтенд.
    document_version VARCHAR(40)  NOT NULL,
    accepted_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Доказательная часть: откуда и чем принимали. Хранится ради спора, а не
    -- ради экрана, поэтому ни в один ответ не попадает.
    source_ip        VARCHAR(45),
    user_agent       VARCHAR(400),
    CONSTRAINT pk_legal_consent PRIMARY KEY (id),
    -- Ровно шесть документов ConsentVersionsView. Набор закрыт, потому что
    -- старый сервис и так перебирал эти шесть ключей, а любые другие молча
    -- выбрасывал: произвольная карта выражала только возможность опечатки.
    CONSTRAINT ck_legal_consent_document CHECK (document IN
        ('agreement', 'privacy', 'personal_data', 'community', 'recording', 'divisions')),
    -- Повторное принятие той же версии — не событие. Уникальность делает
    -- запись согласия идемпотентной без чтения перед вставкой (§6.4).
    CONSTRAINT ux_legal_consent_document UNIQUE (player_id, document, document_version)
);

-- «Что игрок принял по этому документу последним» — вопрос допуска к игре.
CREATE INDEX ix_legal_consent_player
    ON v2.legal_consent (player_id, document, accepted_at DESC);

-- «Кто ещё не принял новую версию» — обратный вопрос, его задаёт рассылка.
CREATE INDEX ix_legal_consent_version ON v2.legal_consent (document, document_version);


-- Заявка на ник, который занять самому не удалось.
--
-- Наследует support_request, потерявшую по дороге type и destination: §6.3
-- числит их мёртвыми, а вид заявки был всего один — «хочу этот ник».
CREATE TABLE v2.nickname_change_request (
    id                 BIGINT      GENERATED ALWAYS AS IDENTITY,
    player_id          UUID        NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    -- Ник на момент заявки: снимок, а не ссылка. Пока владелец сервиса
    -- разбирает заявку, игрок может сменить ник, и разбирать тогда будет
    -- нечего — «было» обязано пережить «стало».
    current_nickname   VARCHAR(20),
    requested_nickname VARCHAR(20) NOT NULL,
    reason             VARCHAR(500),
    status             VARCHAR(16) NOT NULL DEFAULT 'new',
    -- Авторство решения: без внешнего ключа (см. шапку файла).
    decided_by         UUID,
    decided_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_nickname_change_request PRIMARY KEY (id),
    CONSTRAINT ck_nickname_change_request_status CHECK (status IN ('new', 'approved', 'declined')),
    -- Решение и его время появляются вместе; кто решил — может остаться
    -- пустым, если учётку администратора потом удалили.
    CONSTRAINT ck_nickname_change_request_decided CHECK ((status = 'new') = (decided_at IS NULL))
);

-- Одна открытая заявка на игрока: иначе нетерпеливый игрок кладёт в очередь
-- десять просьб об одном и том же нике.
CREATE UNIQUE INDEX ux_nickname_change_request_open
    ON v2.nickname_change_request (player_id) WHERE status = 'new';

-- Очередь разбора у владельца сервиса: только неразобранные, в порядке
-- поступления. Частичный, потому что разобранные в очередь не входят никогда.
CREATE INDEX ix_nickname_change_request_queue
    ON v2.nickname_change_request (created_at) WHERE status = 'new';

-- «Мои заявки» — то, что контракт обещает показать игроку после отправки.
-- Он же покрывает каскад по учётке: оба индекса выше частичные.
CREATE INDEX ix_nickname_change_request_player
    ON v2.nickname_change_request (player_id, created_at DESC);


-- ═══════════════════════════ admin (модерация) ═══════════════════════════

-- История блокировок, а не флаг.
--
-- Сегодня бан — это булев app_user.banned плюс строка user_ban с причиной,
-- которую заводили не всегда (BanRecordDirectory прямо говорит, что строки
-- может не быть). Снятие бана строку удаляло, поэтому «сколько раз этого
-- человека банили» не знал никто. Здесь снятие — это lifted_at, и история
-- остаётся.
--
-- Ключ — суррогат, потому что банов у игрока со временем много; «забанен ли
-- он сейчас» отвечает частичный уникальный индекс ниже.
CREATE TABLE v2.user_ban (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY,
    player_id  UUID         NOT NULL REFERENCES v2.user_account (player_id) ON DELETE CASCADE,
    reason     VARCHAR(300) NOT NULL,
    -- Авторство: без внешнего ключа (см. шапку файла).
    banned_by  UUID         NOT NULL,
    banned_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    lifted_at  TIMESTAMPTZ,
    lifted_by  UUID,
    CONSTRAINT pk_user_ban PRIMARY KEY (id),
    -- BAN_SELF_FORBIDDEN на уровне базы: самобан выглядит как опечатка в
    -- чужом uid, но стоит администратору доступа в собственную панель.
    CONSTRAINT ck_user_ban_self CHECK (player_id <> banned_by),
    CONSTRAINT ck_user_ban_lifted CHECK ((lifted_at IS NULL) = (lifted_by IS NULL))
);

-- Активный бан ровно один. Он же — ответ GET /api/v2/auth/me/ban-state и
-- то, что проверяет фильтр токенов; сегодня на этот вопрос отвечает флаг в
-- карточке игрока, то есть второй источник правды.
CREATE UNIQUE INDEX ux_user_ban_active
    ON v2.user_ban (player_id) WHERE lifted_at IS NULL;

-- Лента блокировок в консоли администратора.
CREATE INDEX ix_user_ban_recent ON v2.user_ban (banned_at DESC);

-- «Сколько раз этого человека банили» — вопрос, на который сегодня ответить
-- нечем: снятие удаляло строку. Он же покрывает каскад по учётке, чего
-- частичный ux_user_ban_active сделать не может.
CREATE INDEX ix_user_ban_player ON v2.user_ban (player_id, banned_at DESC);


-- След действий модератора: кто, что и над чем сделал.
--
-- Сегодня следа нет вовсе — LiftBanUseCase прямо пишет в журнал приложения,
-- что автора снятия сохранить некуда. Отсюда же берутся «сигналы на мемы»:
-- отдельной таблицы meme_report в плане нет намеренно (§6.3) — жалоб на мемы
-- не существует ни в одной операции аудита, а delete_meme_alert означает
-- «админ снял мем с публикации». Это действие модератора, и здесь оно —
-- строка с subject_type = 'meme'.
--
-- details — один из двух jsonb на всю базу, и он такой не по лени: набор
-- полей зависит от вида действия (у снятия мема — причина и слаг, у закрытия
-- комнаты — сколько игроков выставили), и колонок под объединение всех видов
-- было бы больше, чем видов.
CREATE TABLE v2.moderation_action (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    -- Авторство: без внешнего ключа (см. шапку файла). Здесь это особенно
    -- важно — журнал обязан пережить удаление учётки модератора.
    actor_player_id UUID         NOT NULL,
    action          VARCHAR(40)  NOT NULL,
    subject_type    VARCHAR(16)  NOT NULL,
    -- Строкой, а не uuid: у комнаты ключ hat-<hex16>, у мема — meme-<...>,
    -- у игрока — uuid. Приводить их к одному типу нечем.
    subject_id      VARCHAR(180) NOT NULL,
    details         JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_moderation_action PRIMARY KEY (id),
    CONSTRAINT ck_moderation_action_subject CHECK (subject_type IN
        ('player', 'room', 'meme', 'recording'))
);

-- Лента консоли: последние решения модераторов.
CREATE INDEX ix_moderation_action_recent ON v2.moderation_action (created_at DESC);

-- «Что уже делали с этим мемом (комнатой, игроком)» — вопрос перед решением.
CREATE INDEX ix_moderation_action_subject
    ON v2.moderation_action (subject_type, subject_id, created_at DESC);


-- ═════════════════ Что стало с тридцатью шестью колонками app_user ═════════════════
--
-- Переехали как есть (6):
--   uid, email, password_hash, token_version, registered_at, password_updated_at
--                                          → v2.user_account (registered_at = member_since)
--   guest                                  → v2.user_account.kind
--   banned                                 → v2.user_ban + token_version
--   nickname                               → v2.player_profile.nickname
--   display_name                           → он же (§3.2: одно понятие под двумя именами)
--   division_language, ui_language, division_locked_at → v2.player_profile
--   avatar_data_url, avatar_updated_at     → v2.player_avatar (двоичным телом)
--   last_seen_at, active_room_id           → v2.player_presence
--   adult_confirmed                        → v2.player_profile.adult_confirmed_at
--   legal_versions (jsonb)                 → строки v2.legal_consent
--
-- Уехали в чужие кластеры (5):
--   ranked_team_id, ranked_team_status     → v2.ranked_team_member (V11)
--   sabotage_unlimited, sabotage_games_used, default_meme_loadout
--                                          → sabotage_entitlement, player_default_loadout
--
-- Выброшены как мёртвые или выводимые (9, §6.3):
--   legal_accepted, legal_accepted_at      — выводимы из журнала согласий
--   division_backfilled_at, division_explicitly_chosen_at, last_login_at,
--   active_room_updated_at, default_meme_loadout_updated_at,
--   default_meme_loadout_confirmed_at      — пишутся, не читаются
--   ranked_word_history                    — принадлежит партии, не учётке
--   created_at/updated_at                  — updated_at был версией документа;
--                                            её заменил @Version там, где он нужен
--
-- Отдельные таблицы, ушедшие целиком:
--   nickname_index                         — заменена ux_player_profile_nickname
--   support_request                        — v2.nickname_change_request без type/destination
