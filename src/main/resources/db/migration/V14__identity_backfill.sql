-- Перенос личности из app_user в кластер v2, заведённый миграцией V13.
--
-- ─────────────────── Зачем эта миграция вообще нужна ───────────────────
-- V13 завела одиннадцать пустых таблиц, а этот шаг переключил на них весь
-- код: вход, регистрация, обмен токена, карточка игрока, согласия, реестр
-- учёток и блокировки читают и пишут теперь только их. Без переноса каждая
-- существующая учётка перестала бы существовать в тот же миг: человек с
-- живым паролем получил бы INVALID_CREDENTIALS, а владелец сервиса потерял
-- бы доступ в собственную консоль.
--
-- План (§11.1) разрешает пересобирать базу целиком, и тогда переносить
-- нечего. Но разрешение — не обязанность: перенос дешевле одной ночи
-- разбирательств «почему я больше не могу войти», и он же снимает главный
-- риск шага — расхождение данных между app_user и v2.
--
-- ─────────────────── Что переносится и что нет ───────────────────
-- Переносится всё, у чего в v2 появился хозяин: учётка, живые refresh-токены
-- (сессии в браузерах переживают выкатку), карточка игрока, аватар,
-- присутствие, согласия и блокировки.
--
-- Не переносятся права: списки администраторов лежат в настройках сервиса, а
-- не в базе, и SQL о них не знает. Строку account_role заводит сам вход —
-- IdentityStore.grantConfiguredRoles при первой же регистрации или апгрейде.
-- До тех пор администратора узнают по той же настройке, что и раньше.
--
-- Не переносятся заявки на ник (support_request): у старых заявок нет
-- решения, а у новой таблицы решение и его автор обязательны вместе.
--
-- ─────────────────── Почему всюду ON CONFLICT DO NOTHING ───────────────────
-- Миграция обязана быть повторяемой по смыслу и безопасной на базе, где
-- часть строк уже завели через новый код: свежая регистрация могла случиться
-- между выкаткой кода и прогоном миграции. Конфликт здесь означает «эта
-- строка уже есть и она новее», и правильный ответ — не трогать её.
--
-- ─────────────────── Как считается uuid ───────────────────
-- Той же формулой, что записана в V12 и повторена в Java:
-- md5('player:' || uid). Поэтому строки моста, заведённые V12, и строки этих
-- таблиц указывают на одного и того же человека, и согласовывать их нечем.

-- ─────────────────── Мост идентификаторов ───────────────────
--
-- V12 заполнила его игроками, которые существовали на тот момент. С тех пор
-- их прибавилось, а обратный перевод uuid → uid возможен только по строке
-- моста: md5 не обращается. Без этой досылки учётка, заведённая между V12 и
-- сегодня, не имеет имени, которым её знают комнаты и рейтинги, — и не
-- находится ни реестром владельца, ни входом по почте.
INSERT INTO v2.legacy_id_bridge (v2_id, kind, legacy_id)
SELECT md5('player:' || uid)::uuid, 'player', uid
FROM app_user
ON CONFLICT DO NOTHING;


-- ─────────────────── Учётки ───────────────────
--
-- Род определяется наличием почты, а не старым флагом guest. Причина в
-- ограничениях новой таблицы: участник обязан иметь почту, гость обязан не
-- иметь ни почты, ни пароля. Учётка с guest = false и без почты (такие
-- остались с времён Firebase) под «участника» не подходит и переезжает
-- гостем — иначе вставка упала бы, а вместе с ней и весь прогон.
INSERT INTO v2.user_account (
    player_id, kind, email, password_hash, token_version,
    member_since, password_updated_at, created_at)
SELECT
    md5('player:' || u.uid)::uuid,
    CASE WHEN u.email IS NOT NULL AND btrim(u.email) <> '' THEN 'member' ELSE 'guest' END,
    CASE WHEN u.email IS NOT NULL AND btrim(u.email) <> '' THEN u.email END,
    CASE WHEN u.email IS NOT NULL AND btrim(u.email) <> '' THEN u.password_hash END,
    COALESCE(u.token_version, 0),
    CASE WHEN u.email IS NOT NULL AND btrim(u.email) <> ''
         THEN COALESCE(u.registered_at, u.created_at, now()) END,
    u.password_updated_at,
    COALESCE(u.created_at, now())
FROM app_user u
-- Две учётки с одной почтой в старой схеме возможны (уникальности там не
-- было), а в новой стоит ux_user_account_email. Оставляем ту, что старше:
-- она и есть настоящая, вторая — дубль, заведённый повторной регистрацией.
WHERE u.email IS NULL OR btrim(u.email) = '' OR u.uid = (
        SELECT d.uid FROM app_user d
        WHERE lower(d.email) = lower(u.email)
        ORDER BY COALESCE(d.registered_at, d.created_at), d.uid
        LIMIT 1)
ON CONFLICT DO NOTHING;


-- ─────────────────── Живые сессии ───────────────────
--
-- Хеш в старой таблице — шестьдесят четыре символа шестнадцатеричной записи,
-- в новой — тридцать два байта. decode(..., 'hex') это ровно те же байты,
-- поэтому refresh-токен, лежащий сейчас в localStorage браузера, продолжает
-- работать: человек не будет выброшен из игры выкаткой.
--
-- family_id у каждого своя: цепочек ротации старая схема не знала, и каждый
-- живой токен — сам себе начало цепочки. Это честнее, чем склеить их все в
-- одну: гашение при реюзе не должно выкидывать со всех устройств того, кто
-- ничего не терял.
INSERT INTO v2.refresh_token (
    token_hash, player_id, family_id, issued_at, expires_at, revoked_at, revoked_reason, user_agent)
SELECT
    decode(t.token_hash, 'hex'),
    md5('player:' || t.uid)::uuid,
    md5('family:' || t.token_hash)::uuid,
    t.issued_at,
    t.expires_at,
    t.revoked_at,
    -- Ограничение ck_refresh_token_revoked требует причину вместе с датой, а
    -- старая схема причин не хранила: у перенесённых она «rotated».
    CASE WHEN t.revoked_at IS NOT NULL THEN 'rotated' END,
    t.user_agent
FROM refresh_token t
WHERE t.token_hash ~ '^[0-9a-f]{64}$'
  AND EXISTS (SELECT 1 FROM v2.user_account a WHERE a.player_id = md5('player:' || t.uid)::uuid)
ON CONFLICT DO NOTHING;


-- ─────────────────── Карточки игроков ───────────────────
--
-- Ник обязан пройти тот же шаблон, что проверяет @Nickname, и быть
-- уникальным без учёта регистра — этого требуют CHECK и ux_player_profile_nickname.
-- Старые имена ни того, ни другого не гарантируют: там встречаются кириллица,
-- пробелы, пустые строки и повторы.
--
-- Правило простое и предсказуемое: подходящее имя остаётся как есть; всё
-- остальное — и непригодное, и второе из одинаковых — получает
-- детерминированное «playerXXXXXXXX» из хеша uid. Тот же вид имени
-- подставлял старый движок, когда имени не было вовсе, так что для человека
-- это не новость, а знакомая заглушка.
WITH source AS (
    SELECT
        u.uid,
        md5('player:' || u.uid)::uuid AS player_id,
        COALESCE(NULLIF(btrim(u.nickname), ''), NULLIF(btrim(u.display_name), '')) AS raw_nick,
        COALESCE(NULLIF(btrim(u.division_language), ''), 'ru') AS division_language,
        COALESCE(NULLIF(btrim(u.ui_language), ''), NULLIF(btrim(u.division_language), ''), 'ru') AS ui_language,
        u.division_locked_at,
        CASE WHEN u.adult_confirmed THEN COALESCE(u.legal_accepted_at, u.created_at, now()) END AS adult_confirmed_at,
        COALESCE(u.created_at, now()) AS created_at
    FROM app_user u
), shaped AS (
    SELECT s.*,
           CASE WHEN s.raw_nick ~ '^[A-Za-z][A-Za-z0-9_]{2,19}$'
                THEN s.raw_nick
                ELSE 'player' || substr(md5(s.uid), 1, 8) END AS candidate
    FROM source s
), deduped AS (
    SELECT sh.*,
           row_number() OVER (PARTITION BY lower(btrim(sh.candidate)) ORDER BY sh.uid) AS same_name
    FROM shaped sh
)
INSERT INTO v2.player_profile (
    player_id, nickname, division_language, ui_language,
    division_locked_at, adult_confirmed_at, created_at)
SELECT
    d.player_id,
    CASE WHEN d.same_name = 1 THEN d.candidate ELSE 'player' || substr(md5(d.uid), 1, 8) END,
    d.division_language,
    d.ui_language,
    d.division_locked_at,
    d.adult_confirmed_at,
    d.created_at
FROM deduped d
WHERE EXISTS (SELECT 1 FROM v2.user_account a WHERE a.player_id = d.player_id)
ON CONFLICT DO NOTHING;


-- ─────────────────── Аватары ───────────────────
--
-- Из data-URL достаются тип и байты: таблица хранит двоичное тело, а
-- data-URL собирается обратно на чтении. Строки, которые не подходят под
-- ограничения (чужой тип, битая база64, больше 90 КБ), пропускаются молча —
-- аватар не то, ради чего стоит ронять перенос учёток.
INSERT INTO v2.player_avatar (player_id, media_type, bytes, sha256, updated_at)
SELECT
    md5('player:' || u.uid)::uuid,
    lower(substring(u.avatar_data_url from '^data:(image/(?:webp|jpeg|png));base64,')),
    decode(substring(u.avatar_data_url from '^data:image/(?:webp|jpeg|png);base64,(.*)$'), 'base64'),
    sha256(decode(substring(u.avatar_data_url from '^data:image/(?:webp|jpeg|png);base64,(.*)$'), 'base64')),
    COALESCE(u.avatar_updated_at, u.updated_at, now())
FROM app_user u
WHERE u.avatar_data_url ~ '^data:image/(?:webp|jpeg|png);base64,[A-Za-z0-9+/=]+$'
  AND octet_length(decode(substring(u.avatar_data_url from '^data:image/(?:webp|jpeg|png);base64,(.*)$'), 'base64'))
      BETWEEN 1 AND 92160
  AND EXISTS (SELECT 1 FROM v2.player_profile p WHERE p.player_id = md5('player:' || u.uid)::uuid)
ON CONFLICT DO NOTHING;


-- ─────────────────── Присутствие ───────────────────
--
-- Строка заводится каждому: её ждёт отметка «я в сети», а условный UPDATE
-- без строки не сработал бы ни разу и молча терял бы каждый пинг.
--
-- Метка комнаты переносится только та, что похожа на комнату: ограничение
-- ck_player_presence_room проверяет шаблон hat-<16 hex>, а в старой колонке
-- лежали и пустые строки, и мусор от прежних сборок.
INSERT INTO v2.player_presence (player_id, last_seen_at, active_room_id, active_room_at)
SELECT
    md5('player:' || u.uid)::uuid,
    CASE WHEN COALESCE(u.last_seen_at, 0) > 0
         THEN to_timestamp(u.last_seen_at / 1000.0)
         ELSE COALESCE(u.created_at, now()) END,
    CASE WHEN u.active_room_id ~ '^hat-[0-9a-f]{16}$' THEN u.active_room_id END,
    CASE WHEN u.active_room_id ~ '^hat-[0-9a-f]{16}$'
         THEN COALESCE(to_timestamp(NULLIF(u.active_room_updated_at, 0) / 1000.0), now()) END
FROM app_user u
WHERE EXISTS (SELECT 1 FROM v2.user_account a WHERE a.player_id = md5('player:' || u.uid)::uuid)
ON CONFLICT DO NOTHING;


-- ─────────────────── Согласия ───────────────────
--
-- Одно поле jsonb разворачивается в строку на документ. Имя personalData
-- становится personal_data: в таблице набор закрыт CHECK'ом, а в контракте
-- имена остались прежними — перевод живёт в ProfileStore и здесь.
--
-- Источник — app_user.legal_versions, а не таблица legal_consent: именно по
-- профилю проверялся допуск к игре, и именно он не отставал.
INSERT INTO v2.legal_consent (player_id, document, document_version, accepted_at)
SELECT
    md5('player:' || u.uid)::uuid,
    CASE e.key WHEN 'personalData' THEN 'personal_data' ELSE e.key END,
    left(btrim(e.value), 40),
    COALESCE(u.legal_accepted_at, u.created_at, now())
FROM app_user u
CROSS JOIN LATERAL jsonb_each_text(u.legal_versions) AS e(key, value)
WHERE u.legal_versions IS NOT NULL
  AND e.key IN ('agreement', 'privacy', 'personalData', 'community', 'recording', 'divisions')
  AND btrim(COALESCE(e.value, '')) <> ''
  AND EXISTS (SELECT 1 FROM v2.user_account a WHERE a.player_id = md5('player:' || u.uid)::uuid)
ON CONFLICT DO NOTHING;


-- ─────────────────── Блокировки ───────────────────
--
-- Источник факта — флаг app_user.banned: именно он гасил доступ. Строка
-- user_ban была пояснением к флагу и могла отсутствовать, поэтому причина
-- берётся из неё, а когда её нет — подставляется та же, что подставлял
-- администратор по умолчанию.
--
-- Автор блокировки в новой таблице обязателен и не может совпадать с
-- заблокированным. Когда старая строка автора не знает, подставляется
-- служебный идентификатор: он не совпадает ни с одним игроком, обратный
-- перевод по мосту даст пусто, и в консоли автор будет назван неизвестным.
-- Это честнее, чем приписать бан первому попавшемуся администратору.
INSERT INTO v2.user_ban (player_id, reason, banned_by, banned_at)
SELECT
    md5('player:' || u.uid)::uuid,
    left(COALESCE(NULLIF(btrim(b.reason), ''), 'Заблокирован администратором'), 300),
    CASE
        WHEN b.created_by IS NOT NULL AND b.created_by <> u.uid
            THEN md5('player:' || b.created_by)::uuid
        ELSE md5('system:migration')::uuid
    END,
    COALESCE(b.created_at, u.updated_at, now())
FROM app_user u
LEFT JOIN user_ban b ON b.uid = u.uid
WHERE u.banned
  AND EXISTS (SELECT 1 FROM v2.user_account a WHERE a.player_id = md5('player:' || u.uid)::uuid)
ON CONFLICT DO NOTHING;
