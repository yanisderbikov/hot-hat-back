# HOT-HAT — план API v2 и схемы базы

Итоговый документ для согласования. Основания: `API-AUDIT.md` (~131 операция за 36 маппингами),
`DOMAIN-PLAN.md` (карта доменов, 35 клиентских записей), победившее дерево «область продукта»,
пять кластеров схемы, замечания двух судей. Все имена классов, путей, таблиц и ссылок на код —
настоящие; операций сверх аудита и клиентских записей не добавлено, кроме шести, названных поимённо
в §3 (каждая закрывает потерю, найденную судьями в коде).

---

## 1. Что решено

**1. Группировка — по логике пути.** Первый сегмент после `/api/v2/` называет область продукта теми
словами, которыми её зовут заказчик и интерфейс: `auth`, `profile`, `friends`, `chat`, `team`, `lobby`,
`room`, `game`, `rating`, `media`, `recording`, `admin`, `machine`, `app`. Проверка одна: человек смотрит
на URL и говорит «это про друзей», «это про партию», не открывая код. Ресурс живёт глубже
(`/api/v2/friends/requests/{id}`), действие — под-ресурсом (`/acceptance`, `/rejection`, `/draw`,
`/reset`, `/closing`, `/promotion`), а не строкой в теле. Версия — **v2**: `v1` в проекте уже занят
черновиком и путаница дороже цифры.

Из этого следует: класс контроллера приписан к своему поддереву пути; переименования разрешены и
сделаны там, где нынешнее имя врёт (§3); `portal`, `social`, `db`, `documents`, `platform`,
`observability`, `speech`, `bff` исчезают из словаря целиком.

**Две области названы не по предмету, а по поверхности, и это объявлено вслух.** `admin` — это
консоль администратора, настоящий экран продукта (`public/admin.html`, `public/admin.js`) со своей
аудиторией и своими проекциями. `machine` — межсервисная поверхность: рекордер, вебхук Egress, cron,
агент мониторинга. Основание проверяется по коду: сегодня все четыре машинных маршрута лежат в
`permitAll` (`WebSecurityConfig.java:58-62`), а подпись сверяется руками внутри метода. Один
`securityMatcher` на поддерево превращает это в декларацию. Цена — дублирование ресурса по двум
адресам — снята композицией вложенных view (§10), а не ручной синхронизацией двенадцати пар DTO.

**2. Объём — весь план.** Включая комнату, партию и перенос игрового движка с клиента на сервер:
16 клиентских транзакций (12 `runTransaction` + 4 `writeBatch`) и 35 прямых записей исчезают,
розыгрыш слова (`drawRandomWord(bag)`) уезжает на сервер, `POST /api/db/write` не существует.

**3. База пересобирается полностью.** Это решение меняет не только схему, но и способ перехода:
переходного слоя `/api/db` **нет вовсе**. `DocumentServiceImpl` отдаёт `data` как сериализованную
JPA-сущность и ходит мимо репозиториев (аудит §2.3); как только `Room` распадается на `room`,
`room_lifecycle`, `match`, `match_turn`, снимок `rooms/{id}` перестаёт существовать как строка, и
легаси-подписчик молча получил бы документ без половины полей. Поэтому: 92 таблицы новой схемы,
ни одного `jsonb` кроме двух объявленных, костыль `@FieldOwner` не рассматривается, а фронтенд
переключается на v2 **одним переходом на окружение** (§11).

**4. Гостю открыто: лобби, превью комнаты, апгрейд, согласия.** Плюс механика самой сессии (гостевой
вход, обновление пары, выход, чтение своей учётки и своего бана) — без неё гость не проживёт
15 минут. Ничего сверх: ни чата, ни друзей, ни входа в комнату, ни аналитики. Точный список — §8.

---

## 2. Дерево `/api/v2/`

| Префикс | Область | Java-пакет | Что за область | Классов | Эндпоинтов |
|---|---|---|---|---|---|
| `/api/v2/auth` | auth | `ru.hothat.auth` | Личность, сессии, пароли, состояние бана | 6 | 13 |
| `/api/v2/profile` | profile | `ru.hothat.profile` (+ `ru.hothat.sabotage.api` — обойма и квота) | Карточка игрока, присутствие, согласия, справочник дивизионов | 7 | 20 |
| `/api/v2/friends` | friends | `ru.hothat.friend` | Связи и заявки в друзья | 3 | 9 |
| `/api/v2/chat` | chat | `ru.hothat.chat` | Личная переписка | 2 | 7 |
| `/api/v2/team` | team | `ru.hothat.team` | Постоянная рейтинговая команда, префлайт | 6 | 12 |
| `/api/v2/lobby` | lobby | `ru.hothat.lobby` | Витрина открытых комнат, превью, подбор | 4 | 10 |
| `/api/v2/room` | room | `ru.hothat.room` | Комната: места, зрители, составы, чат, хозяин | 12 | 34 |
| `/api/v2/game` | game | `ru.hothat.game` (+ `ru.hothat.sabotage.api` — диверсии) | Партия: ход, слова, апелляция, диверсии | 11 | 26 |
| `/api/v2/rating` | rating | `ru.hothat.rating` | Сезонные таблицы, запись результата | 2 | 4 |
| `/api/v2/media` | media | `ru.hothat.media` | Мемы и их файлы | 3 | 9 |
| `/api/v2/recording` | recording | `ru.hothat.recording` | Записи партий | 3 | 8 |
| `/api/v2/admin` | admin | `ru.hothat.<владелец>.api.admin` | Консоль администратора и владельца | 7 | 23 |
| `/api/v2/machine` | machine | `ru.hothat.machine` + командные порты владельцев | Рекордер, вебхук Egress, cron, агент мониторинга | 6 | 12 |
| `/api/v2/app` | app | `ru.hothat.app` | Среда приложения: проба живости, флаг, телеметрия | 2 | 3 |
| `/ws/v2` | realtime | `ru.hothat.realtime` | Именованные каналы живых обновлений | 8 | 7 каналов + 3 кадра |
| **Итого** | **14 областей** | | | **82** | **190 HTTP + 10 WS** |

Среднее — 2,57 эндпоинта на HTTP-класс при 74 HTTP-классах. Против 137 классов отвергнутого варианта
и 87 классов победившего дерева. Почему не 3-4 — в §13, риск 5.

```
/api/v2
├── auth
│   ├── sessions            POST · POST guest · POST renewal · DELETE current
│   ├── accounts            POST · GET nickname-availability · POST upgrades
│   ├── password-resets     POST · POST completions
│   └── me                  GET · GET ban-state · security/{password,sessions}
├── profile
│   ├── me                  GET · nickname · avatar · ui-language · nickname-requests
│   │                       onboarding · division · consents
│   │                       meme-loadout · sabotage-entitlement
│   ├── presence            me · summary · me/room
│   ├── players/{uid}       GET
│   └── divisions           GET · GET suggestion
├── friends                 GET · presence · {friendUid}
│   └── requests            by-nickname · by-player · incoming · outgoing
│       └── {requestId}     acceptance · rejection
├── chat                    threads · inbox
│   └── {peerUid}           messages · image-messages · recording-messages · read-mark
├── team                    POST · invites
│   ├── invites/{inviteId}  acceptance · rejection
│   ├── me                  GET · lobby · preflight{,/participants/me}
│   └── {teamId}            GET
├── lobby
│   ├── rooms               GET · {roomId} · {roomId}/preview-session
│   ├── tickets             casual · {ticketId}
│   └── ranked              tickets · rooms/{roomId}/entry
├── room                    POST
│   ├── invites             GET · {inviteId}/acceptance
│   └── {roomId}            GET · name · turn-duration · reset · DELETE
│       ├── players         me{,/devices,/heartbeat,/video-token} · {uid}
│       ├── spectators      me{,/heartbeat,/video-token} · {uid}/promotion
│       ├── teams           POST · {teamId} · {teamId}/members/me · draw
│       ├── chat-messages   GET · POST · {messageId} · chat-images
│       ├── host            PUT · heartbeat · handover
│       ├── invites         POST
│       ├── public-presence PUT
│       └── recording-preference PUT
├── game
│   ├── weapons             GET
│   ├── clock               GET
│   └── {roomId}            POST · GET · pause · ranked-autostart
│       ├── word-submissions/me PUT
│       ├── players/me      heartbeat · departure · disconnection
│       │                   arsenal · loadout{,/slots/{i}}
│       ├── turn            POST · words/{wordId}/{guess,skip} · completion · expiry · next
│       ├── appeal          votes/{wordId} · closing
│       ├── sabotages       POST
│       └── replacement-clips POST · {clipId}/result · DELETE {clipId}
├── rating                  teams · players · champion · match-results
├── media
│   ├── memes               GET · GET {memeId} · POST · DELETE {memeId}
│   ├── tickets             video-uploads · poster-uploads · playbacks
│   ├── orphan-files        DELETE
│   └── files/{*path}       GET → 302
├── recording
│   ├── rooms/{roomId}/games/{gameNumber}  POST · finish · GET · recorder-readiness
│   ├── mine                GET · PUT {id} · DELETE {id}
│   └── {recordingId}/playback-urls GET
├── admin
│   ├── dashboard           live-rooms · usage · host-metrics · configuration · object-storage
│   ├── usage-snapshots     GET · POST
│   ├── bans                POST · DELETE {uid}
│   ├── rooms/{roomId}/closure POST
│   ├── recordings          GET · {id}/playback-urls · DELETE {id}
│   ├── memes               {memeId}/optimized-variant · library-reconciliations · DELETE {memeId}
│   ├── users               GET
│   └── test-rooms/{roomId}/bots POST · DELETE · turn · video-tokens · owner-farts · speech-syntheses
├── machine
│   ├── recorder            sessions · rooms/{roomId}/games/{n}/{scene,room-state,current-word}
│   │                       rooms/{roomId}/games/{n}/{ready,start}-signal · ceremony-completion
│   ├── webhooks/livekit-egress POST
│   ├── maintenance         room-sweeps · recording-sweeps · token-sweeps
│   └── usage-snapshots     POST
└── app                     health · features/{name} · analytics-events

/ws/v2
├── lobby                   витрина + кадр spotlight (превью выбранной комнаты в том же сокете)
├── room/{roomId}           комната, игроки, составы, зрители, чат, состояние партии
├── me/social               заявки, инбокс, бан
├── chat/{peerUid}          личная переписка
├── team/{teamId}/preflight предматчевая проверка
├── media/memes             библиотека мемов
├── machine/recorder/rooms/{roomId} зеркало сцены для рекордера
└── **                      кадры: ping · spotlight · unsubscribe
```

**Затенение путей закрыто регуляркой в маппинге, а не соглашением.** `/api/v2/room/invites` соседствует
с `/api/v2/room/{roomId}`, `/api/v2/team/invites` и `/api/v2/team/me` — с `/api/v2/team/{teamId}`.
Идентификаторы объявляются как `@GetMapping("/{roomId:hat-[0-9a-f]{16}}")` и
`"/{teamId:rt-[0-9a-f]{16}}"`, поэтому литеральный сегмент физически не может быть перехвачен.

---

## 3. Переименования

### 3.1 Понятия, чьё нынешнее имя врёт

| Сегодня | В v2 | Почему имя врало |
|---|---|---|
| `portal` (44 действия) | `profile`, `friends`, `chat`, `team`, `lobby`, `rating`, `room` | Слово называет оболочку фронтенда, а не область данных. За ним прятались шесть предметных областей. |
| `social` | `friends` + `chat` | «Социальное» — мешок. У дружбы и у сообщения разный объём, разный срок жизни, разные права. |
| `game` (17 действий) | `room` (всё, что живёт до старта и переживает партию) + `game` (только партия) | Сегодня `POST /api/game` — это весь игровой экран, включая кик, передачу хозяйства и настройку записи. |
| `room` (список + комната) | `lobby` (витрина, её видит гость) + `room` (комната, её видит участник) | Одно слово несло две аудитории, две проекции и два уровня прав. |
| `recording-state` | `machine/recorder/**`, «состояние» → «сцена» | Рекордер снимает сцену, а не читает состояние комнаты. Три мутирующих GET (`ready`, `started`, `ceremony_done`) стали POST-сигналами, подпись уехала из query в заголовок. |
| `media` (мемы + записи) | `media` (мемы) + `recording` (записи партий) | У записи своя библиотека, своё право на просмотр (`canWatch`) и свой срок хранения. |
| `matchmake` | `lobby/tickets` | Подбор — не область, а заявка, живущая на экране лобби. |
| `ratings` (один ответ, три массива) | `rating/teams`, `rating/players`, `rating/champion` | `portal.js:150` и так перезапрашивает всё при переключении вкладки — три ресурса дают меньше трафика. |
| `test-bots` | `admin/test-rooms/{roomId}/bots` | Тест-режим — админский инструмент, а не область продукта. |
| `db` / `documents` | ресурсы и семь именованных каналов | Путь строкой, коллекция строкой и «документ» как единица обмена уходят из словаря целиком. |
| `delete_meme_alert` | `DELETE /api/v2/admin/memes/{memeId}` | «Алерт» в продукте — это сам мем-ролик (`app-core.js:5893`), а не жалоба. Действие всегда было «админ снял мем с публикации». |
| `POST /api/v1/rating/results` (черновик) | `POST /api/v2/rating/match-results` | «Результаты рейтинга» читается как чтение таблицы; это отправка результата партии. |

### 3.2 Дубли, слитые в одно понятие

| Сегодня | В v2 | Основание |
|---|---|---|
| `resetRoom()` + `runTransaction backToSetup()` | `POST /api/v2/room/{roomId}/reset` | Одно понятие под двумя именами (`app-core.js:13570`, `:13508`). |
| `repair_nickname_indexes` + `migrate_nicknames` | **удалены** | Один метод под двумя именами; вместе с таблицей `nickname_index` (§6) исчезает и повод их звать. |
| `displayName` + `nickname` | `nickname` | Все писатели ставят их одинаково (`AuthServiceImpl:60-61,100-101`, `ProfileServiceImpl:64-65,207-208,612-613`); отдельно `displayName` меняет один метод и порождает состояние «ник Vasya, имя Петя», которое потом лечит `resolvedNickname`. `PATCH /api/auth/profile` схлопывается в `PUT /api/v2/profile/me/nickname`. |
| `sync_room_nickname` | **удалён** | `room_player.name` выброшен из схемы: имя берётся `ProfileDirectoryPort`, синхронизировать нечего. Вместе с ним исчезает `propagateNickname` (`ProfileServiceImpl:198-260`). |
| `test_bot_voice_effect_*` + `test_bot_special_effect_*` | `test_bot_schedule.special_effect_*` | В коде прямо написано, что старые поля обновляются «ради совместимости с прежними клиентами». |

### 3.3 Дискриминаторы, ставшие адресами

| Сегодня — поле тела | В v2 — адреса |
|---|---|
| `answer_friend {accept}` | `POST …/requests/{id}/acceptance` · `POST …/requests/{id}/rejection` |
| `accept_team` / `decline_team` (флаг выводился из имени действия в контроллере) | `POST …/invites/{id}/acceptance` · `POST …/invites/{id}/rejection` |
| `toggle_pause {paused}` | `PUT /api/v2/game/{roomId}/pause` · `DELETE /api/v2/game/{roomId}/pause` |
| `matchmake {ranked}` | `POST /api/v2/lobby/tickets/casual` · `POST /api/v2/lobby/ranked/tickets` (за вторым — капитан и пройденный префлайт) |
| `send_chat {attachment.kind}` | `POST …/messages` · `POST …/image-messages` · `POST …/recording-messages` |
| `upload_ticket {kind}` | `POST /api/v2/media/tickets/video-uploads` · `POST …/poster-uploads` |
| `GET /api/admin?scope=` | `…/dashboard/live-rooms` · `…/dashboard/usage` (`scope=all` удалён) |
| `POST /api/monitor` (две схемы входа — две формы ответа) | `POST /api/v2/admin/usage-snapshots` · `POST /api/v2/machine/usage-snapshots` |
| `GET /api/recording-state` (7 режимов query) | семь адресов `/api/v2/machine/recorder/**` |
| `POST /api/token {role}` | четыре адреса: игрок, зритель, превью, тест-бот |
| `PATCH /rooms/{id}` (черновик: имя **и** длительность хода в одном DTO) | `PUT /api/v2/room/{roomId}/name` · `PUT /api/v2/room/{roomId}/turn-duration` |
| `sync_game_presence {reason}` (9 значений) | `PUT …/players/me/heartbeat` · `POST …/players/me/departure` · `POST …/players/me/disconnection` |

**Про `departure` отдельно, потому что предыдущий круг здесь ошибся.** Черновик предлагал принимать
пустое тело и спрашивать реальность обрыва у LiveKit. Это опровергается кодом: `GameServiceImpl:632-634`
комментарием объясняет, что отключение фиксируется **до** опроса LiveKit, «его ростер отдаёт ушедшего
ещё несколько секунд»; а `:676-686` показывает, что три причины не равнозначны — только
`livekit-disconnected` при полном отсутствии состава сносит комнату. Поэтому не ноль информации и не
девять значений, а **два адреса**: `departure` (`browser-pagehide`, `livekit-reconnecting` — сервер
помечает ушедшего, ждёт возвращения) и `disconnection` (`livekit-disconnected` — то же плюс право
закрыть комнату, если не осталось никого). Поведение сохранено, строкового дискриминатора нет.

### 3.4 Понятия, которых больше нет

| Что | Почему нет замены |
|---|---|
| `portal cleanup_rooms` | Дубль без единой проверки прав; обнуляет рейтинговые результаты до записи MMR (A2). Фронт не зовёт его ни разу. |
| `POST /api/cleanup-rooms?room_id=` из клиента (`app-core.js:1045-1051`, зовётся при выходе любым игроком) | Немедленная уборка опустевшей комнаты стала **инвариантом** `LeaveRoomUseCase`: ответ `DELETE …/players/me` несёт `roomClosed`. Отдельного адреса «снеси комнату, если пусто» не нужно, и `DELETE /api/v2/room/{roomId}` остаётся честно хозяйским. |
| `setup_host_watch` как «дозор» | Стал `POST /api/v2/room/{roomId}/host/handover` с полным ответом (см. ниже). В heartbeat **не сливается**. |
| `GET /api/admin?scope=all` | Склейка двух ответов. `admin.js:271` и `:378` переписываются на два запроса — сегодня они зовут `all`, и это надо сделать явно. |
| GET-варианты `/api/cleanup-*` | Мутация на GET. |
| Блок `env` в публичном `/api/health` | Уезжает под ADMIN в `…/dashboard/configuration` (A13). |
| `room_name` в `/api/token` | Сервер не читал его никогда (C15); поле убирается из `livekit.js:541` и `live-preview.js:104`. |
| `expectedUpdatedAt`, `updatedAt` как контракт | Исчезают вместе с документным шлюзом; конкурентность — дело `@Version` и условных UPDATE (§6). |
| `migrate_legacy_memes`, `migrate_meme_media` | Переносят строки из хранилища, которого при чистой пересборке нет. Репопуляцию делает `POST /api/v2/admin/memes/library-reconciliations` (бакет → строки). Заодно исчезает SSRF-поверхность по `meme.src`. |
| `/api/db/document`, `/api/db/query`, `/api/db/write`, `/ws/documents`, весь `compat` | База пересобрана: спроецировать новые таблицы в снимок `rooms/{id}` нечем, кроме рукописной легаси-проекции. Переходного слоя нет вовсе (§11). |
| `bff` / `screens/**` | Два контракта на одни данные и слой, который ничем не владеет. Самый тяжёлый экран закрыт композитным `GET /api/v2/room/{roomId}`, значки шапки — hello-кадром `/ws/v2/me/social`. |
| `platform`, `observability`, `speech` как области | Справочник дивизионов уехал в `profile` (это диапазон значений поля карточки), каталог оружия и часы — в `game`, здоровье и флаг — в `app`, дашборды и снимки — в `admin`, агент — в `machine`, синтез речи — к тест-ботам. |

### 3.5 Шесть операций, добавленных сверх аудита

Каждая закрывает потерю, найденную судьями в коде; ни одна не выдумана «на будущее».

| Новый адрес | Что закрывает |
|---|---|
| `GET /api/v2/auth/me/ban-state` | `live-preview.js:99` и `realtime-social.js:113` подписаны на `bans/{uid}`; гость в превью открыт заказчиком, а канал `/ws/v2/me/social` — только для USER. Без адреса забаненному гостю никто не скажет, что он забанен. |
| `GET /api/v2/media/memes/{memeId}` | `app-core.js:5184` — `getDoc(doc(db,"memeLibrary",id))`, догрузка мема для воспроизведения мимо кеша. В черновике чтения одного мема не было ни в `media`, ни в `admin`. |
| `POST /api/v2/room/{roomId}/host/handover` | Ответ `setup_host_watch` читается по четырём полям (`app-core.js:1016-1024`: `restored`, `transferred`, `newHostName`, `remainingMs`) и рисует два тоста и подсказку. Слияние в heartbeat потеряло бы их и сделало бы каждый пинг пишущей транзакцией. |
| `POST /api/v2/game/{roomId}/players/me/disconnection` | Вторая ветка присутствия: только она вправе закрыть комнату (`GameServiceImpl:676-686`). |
| `WS /ws/v2/machine/recorder/rooms/{roomId}` | `startRecorderDbMirror` (`app-core.js:13836-13876`) подписывает рекордер на `rooms/{id}` и `rooms/{id}/teams`; комментарий в коде объясняет, что зеркало «становится источником правды для самой партии и снимает замороженный первый ход». Ни один продуктовый канал рекордеру не доступен: он не игрок и не зритель. |
| Кадр `spotlight` в `/ws/v2/lobby` | `home/home.js:93` заводит `setInterval(rotateLiveRoom, 30000)`: выбранная комната меняется каждые 30 секунд у каждого посетителя главной, включая гостя. Отдельный канал превью означал бы разрыв и новое рукопожатие каждые полминуты. Кадр возвращает и смысл, и цену прежнего `unsubscribe` (`db.js:389-398`). |

---

## 4. Контроллеры

Правило: **класс = ресурс × уровень прав**. Один `@PreAuthorize` на класс, одна фраза ответственности
без союза «и», ноль ветвлений по строковому аргументу. Путь перестаёт быть 1:1 с классом — три класса
могут сидеть на `/api/v2/game/{roomId}`, потому что у них разные предикаты; это осознанная плата.

### 4.1 `/api/v2/auth` — 6 классов, 13 эндпоинтов

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `SessionController` | `/api/v2/auth/sessions` | Ведёт жизненный цикл клиентской сессии. | `permitAll` (доказательство — предъявленные учётные данные либо refresh-токен) | 4 |
| `AccountRegistrationController` | `/api/v2/auth/accounts` | Обслуживает создание парольной учётной записи. | `permitAll` + `@RateLimited(IP)` | 2 |
| `GuestUpgradeController` | `/api/v2/auth/accounts/upgrades` | Превращает гостевую сессию в парольную учётную запись. | `hasRole('GUEST')` | 1 |
| `PasswordResetController` | `/api/v2/auth/password-resets` | Восстанавливает доступ по одноразовой ссылке. | `permitAll` + `@RateLimited(email, IP)` | 2 |
| `MyAccountController` | `/api/v2/auth/me` | Отдаёт сведения о текущей учётной записи. | `hasAnyRole('USER','GUEST')` | 2 |
| `MySecurityController` | `/api/v2/auth/me/security` | Управляет доступом к собственной учётной записи. | `hasRole('USER')` | 2 |

### 4.2 `/api/v2/profile` — 7 классов, 20 эндпоинтов

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `MyProfileController` | `/api/v2/profile/me` | Ведёт карточку текущего игрока. | `hasRole('USER')` | 5 |
| `ProfileOnboardingController` | `/api/v2/profile/me` | Закрепляет однократный выбор игрока при первом входе. | `hasRole('USER')` | 2 |
| `PresenceController` | `/api/v2/profile/presence` | Ведёт сведения о присутствии игроков в портале. | `hasRole('USER')` | 4 |
| `MyLoadoutController` | `/api/v2/profile/me` | Ведёт диверсионное снаряжение учётной записи. | `hasRole('USER')` | 4 |
| `MyConsentController` | `/api/v2/profile/me/consents` | Регистрирует принятые правовые согласия. | `hasAnyRole('USER','GUEST')` | 2 |
| `PublicPlayerController` | `/api/v2/profile/players/{uid}` | Показывает публичную карточку игрока. | `hasRole('USER')` | 1 |
| `DivisionCatalogController` | `/api/v2/profile/divisions` | Отдаёт справочник языковых дивизионов. | `permitAll` | 2 |

`MyLoadoutController` — единственное место, где путь и домен-владелец расходятся намеренно: путь в
области `profile` (набор относится к учётке и правится с экрана профиля), пакет `ru.hothat.sabotage.api`
(правило «пять существующих мемов» — это `LoadoutRules` домена диверсий, и второго писателя у таблицы
нет). Расхождение объявлено, а не спрятано; таких мест ровно три (ещё `SabotageController` и
`MatchLoadoutController` в области `game`).

### 4.3 `/api/v2/friends` — 3 класса, 9 эндпоинтов

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `MyFriendsController` | `/api/v2/friends` | Ведёт круг друзей текущего игрока. | `hasRole('USER')` | 3 |
| `FriendRequestController` | `/api/v2/friends/requests` | Ведёт собственные заявки в друзья. | `hasRole('USER')` | 4 |
| `FriendRequestAnswerController` | `/api/v2/friends/requests/{requestId}` | Разрешает судьбу входящей заявки. | `@friendAuthz.isRecipient(#requestId)` | 2 |

### 4.4 `/api/v2/chat` — 2 класса, 7 эндпоинтов

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `ChatOverviewController` | `/api/v2/chat` | Отдаёт сводку личных переписок. | `hasRole('USER')` | 2 |
| `ChatMessageController` | `/api/v2/chat/{peerUid}` | Ведёт сообщения одной личной переписки. | `@chatAuthz.areFriends(#peerUid)` | 5 |

### 4.5 `/api/v2/team` — 6 классов, 12 эндпоинтов

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `TeamEntryController` | `/api/v2/team` | Обслуживает игрока, у которого ещё нет команды. | `hasRole('USER')` | 2 |
| `TeamInviteAnswerController` | `/api/v2/team/invites/{inviteId}` | Разрешает судьбу приглашения в команду. | `@teamInviteAuthz.isInvitee(#inviteId)` | 2 |
| `MyTeamController` | `/api/v2/team/me` | Ведёт постоянную команду текущего игрока. | `@teamAuthz.isMember` | 2 |
| `PublicTeamController` | `/api/v2/team/{teamId}` | Показывает публичную карточку команды. | `hasRole('USER')` | 1 |
| `PreflightSessionController` | `/api/v2/team/me/preflight` | Ведёт сессию предматчевой проверки. | `@teamAuthz.isActiveMember` | 3 |
| `PreflightParticipantController` | `/api/v2/team/me/preflight/participants/me` | Принимает отчёт участника о собственной готовности. | `@preflightAuthz.isParticipant` | 2 |

### 4.6 `/api/v2/lobby` — 4 класса, 10 эндпоинтов

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `LobbyDirectoryController` | `/api/v2/lobby/rooms` | Показывает открытые комнаты всем вошедшим. | `hasAnyRole('GUEST','USER')` | 2 |
| `RoomPreviewSessionController` | `/api/v2/lobby/rooms/{roomId}/preview-session` | Пускает наблюдателя в видеопревью комнаты. | `hasAnyRole('GUEST','USER')` | 3 |
| `MatchTicketController` | `/api/v2/lobby/tickets` | Ведёт заявку игрока на подбор комнаты. | `hasRole('USER')` | 3 |
| `RankedTicketController` | `/api/v2/lobby/ranked` | Заводит рейтинговую команду в подбор. | `@teamAuthz.isCaptain` | 2 |

Подбор перестаёт быть побочным эффектом опроса. Сегодня `home/home.js` зовёт `matchmake` каждые 3-5 с,
а `MatchmakingServiceImpl:96-127` на каждом вызове заново перебирает комнаты фазы `setup`, из-за чего
повторный вызов может увести игрока в другую комнату. В v2 заявка создаётся один раз и адресуется
собственным `ticketId`; сведение заявок в комнаты выполняет **внутренний планировщик**
`RunMatchmakingUseCase` (`@Scheduled`, домен `lobby`, HTTP-адреса нет), а `GET …/tickets/{ticketId}` —
чистое чтение.

### 4.7 `/api/v2/room` — 12 классов, 34 эндпоинта

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `RoomEntryController` | `/api/v2/room` | Заводит игрока в комнату. | `hasRole('USER')` | 3 |
| `RoomController` | `/api/v2/room/{roomId}` | Отдаёт снимок комнаты её участникам. | `@roomAuthz.isMemberOrSpectator(#roomId)` | 1 |
| `MyRoomSeatController` | `/api/v2/room/{roomId}/players/me` | Ведёт собственное место игрока в комнате. | `@roomAuthz.isMember(#roomId)` | 4 |
| `MySpectatorSeatController` | `/api/v2/room/{roomId}/spectators/me` | Ведёт собственное место зрителя в комнате. | `@roomAuthz.isSpectator(#roomId)` | 3 |
| `RoomTeamController` | `/api/v2/room/{roomId}/teams` | Ведёт составы команд внутри комнаты. | `@roomAuthz.isMember(#roomId)` | 5 |
| `RoomChatController` | `/api/v2/room/{roomId}` | Ведёт чат внутри комнаты. | `@roomAuthz.isMemberOrSpectator(#roomId)` | 4 |
| `RoomHostController` | `/api/v2/room/{roomId}` | Распоряжается комнатой от имени хозяина. | `@roomAuthz.isHost(#roomId)` | 7 |
| `RoomHostHandoverController` | `/api/v2/room/{roomId}/host/handover` | Передаёт осиротевшее хозяйство комнаты. | `@roomAuthz.isMember(#roomId)` | 1 |
| `RoomRosterController` | `/api/v2/room/{roomId}` | Меняет состав комнаты решением хозяина. | `@roomAuthz.isHost(#roomId)` | 3 |
| `RoomInviteStatusController` | `/api/v2/room/invites` | Показывает состояние выданных приглашений. | `hasRole('USER')` | 1 |
| `RoomInviteAcceptanceController` | `/api/v2/room/invites/{inviteId}` | Принимает приглашение в комнату. | `@roomInviteAuthz.isRecipient(#inviteId)` | 1 |
| `RoomRecordingPreferenceController` | `/api/v2/room/{roomId}/recording-preference` | Включает запись партий в комнате. | `hasRole('OWNER')` | 1 |

Приглашения разрезаны на два класса намеренно. `GET /api/v2/room/invites` принимает до 60 идентификаторов
(`realtime-social.js:71-77`) и отдаёт построчные статусы, включая `forbidden`, — предикат уровня класса к
такому списку неприменим в принципе. Поэтому чтение живёт под `hasRole('USER')` и режет видимость
построчно **внутри проекции**, а не в правах; принятие — под предикатом одного получателя.

### 4.8 `/api/v2/game` — 11 классов, 26 эндпоинтов

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `MatchLifecycleController` | `/api/v2/game/{roomId}` | Распоряжается течением партии от имени хозяина. | `@roomAuthz.isHost(#roomId)` | 3 |
| `MatchParticipationController` | `/api/v2/game/{roomId}` | Обслуживает участие игрока в партии. | `@roomAuthz.isMember(#roomId)` | 3 |
| `MatchDepartureController` | `/api/v2/game/{roomId}/players/me` | Принимает от игрока сигнал о разрыве участия. | `@roomAuthz.isMember(#roomId)` | 2 |
| `MatchStateController` | `/api/v2/game/{roomId}` | Отдаёт состояние текущей партии. | `@roomAuthz.isMemberOrSpectator(#roomId)` | 1 |
| `TurnController` | `/api/v2/game/{roomId}/turn` | Ведёт ход объясняющего игрока. | `@gameAuthz.isExplainer(#roomId)` | 4 |
| `TurnClockController` | `/api/v2/game/{roomId}/turn` | Продвигает партию по серверным часам. | `@roomAuthz.isMember(#roomId)` | 2 |
| `AppealController` | `/api/v2/game/{roomId}/appeal` | Ведёт апелляцию по спорному слову. | `@gameAuthz.isPlayer(#roomId)` | 2 |
| `MatchLoadoutController` | `/api/v2/game/{roomId}/players/me` | Ведёт боевое снаряжение игрока в партии. | `@gameAuthz.isPlayer(#roomId)` | 3 |
| `SabotageController` | `/api/v2/game/{roomId}` | Ведёт диверсионные действия игрока в партии. | `@gameAuthz.isPlayer(#roomId)` | 4 |
| `WeaponCatalogController` | `/api/v2/game/weapons` | Отдаёт каталог диверсионного оружия. | `hasRole('USER')` | 1 |
| `ServerClockController` | `/api/v2/game/clock` | Отдаёт серверное время для игровых часов. | `permitAll` | 1 |

`WeaponCatalogController` и `ServerClockController` переехали из отвергнутой области `app`: каталог
арсенала питает `/api/v2/game/{roomId}/sabotages` (во фронте это `BASE_ARSENAL`, `app-core.js:356`),
а часы калибруют дедлайн хода (`app-core.js:8053-8062`) и нужны странице рекордера. Глядя на
`/api/v2/game/weapons`, человек говорит «это про игру» — чего нельзя было сказать про `/api/v2/app/weapons`.

### 4.9 `/api/v2/rating`, `/api/v2/media`, `/api/v2/recording` — 8 классов, 21 эндпоинт

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `RankingBoardController` | `/api/v2/rating` | Отдаёт сезонные рейтинговые таблицы. | `hasRole('USER')` | 3 |
| `MatchResultSubmissionController` | `/api/v2/rating/match-results` | Записывает результат рейтинговой партии. | `@gameAuthz.isRankedParticipant` | 1 |
| `MemeCatalogController` | `/api/v2/media/memes` | Ведёт библиотеку мемов. | `hasRole('USER')` | 4 |
| `MediaFileAccessController` | `/api/v2/media` | Обслуживает прямой доступ браузера к файлам мемов. | `hasRole('USER')` | 4 |
| `MemeFileController` | `/api/v2/media/files/**` | Перенаправляет на файл мема в хранилище. | `permitAll` | 1 |
| `MatchRecordingController` | `/api/v2/recording/rooms/{roomId}/games/{gameNumber}` | Ведёт запись одной партии. | `@roomAuthz.isMember(#roomId)` | 4 |
| `MyRecordingLibraryController` | `/api/v2/recording/mine` | Ведёт личную библиотеку записей. | `hasRole('USER')` | 3 |
| `RecordingPlaybackController` | `/api/v2/recording/{recordingId}/playback-urls` | Выдаёт ссылки на просмотр записи. | `@recordingAuthz.canWatch(#recordingId)` | 1 |

### 4.10 `/api/v2/admin` — 7 классов, 23 эндпоинта

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `AdminDashboardController` | `/api/v2/admin/dashboard` | Показывает администратору сводки о состоянии сервиса. | `hasRole('ADMIN')` | 5 |
| `AdminUsageSnapshotController` | `/api/v2/admin/usage-snapshots` | Ведёт снимки расхода внешних сервисов. | `hasRole('ADMIN')` | 2 |
| `AdminModerationController` | `/api/v2/admin` | Пресекает нарушения решением администратора. | `hasRole('ADMIN')` | 3 |
| `AdminRecordingController` | `/api/v2/admin/recordings` | Ведёт админский каталог записей. | `hasRole('ADMIN')` | 3 |
| `AdminMemeController` | `/api/v2/admin/memes` | Правит библиотеку мемов решением администратора. | `hasRole('ADMIN')` | 3 |
| `OwnerUserRegistryController` | `/api/v2/admin/users` | Перечисляет учётные записи для владельца сервиса. | `hasRole('OWNER')` | 1 |
| `TestBotController` | `/api/v2/admin/test-rooms/{roomId}/bots` | Ведёт отряд тестовых ботов комнаты. | `hasRole('ADMIN')` | 6 |

### 4.11 `/api/v2/machine` — 6 классов, 12 эндпоинтов

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `RecorderBootstrapController` | `/api/v2/machine/recorder/sessions` | Снаряжает рекордер для съёмки партии. | `hasRole('RECORDER_BOOTSTRAP')` (`RecorderSignatureFilter`, подпись в `X-Hot-Hat-Signature`) | 1 |
| `RecorderSceneController` | `/api/v2/machine/recorder/rooms/{roomId}/games/{gameNumber}` | Отдаёт рекордеру состояние снимаемой сцены. | `hasRole('RECORDER')` | 3 |
| `RecorderProgressController` | `/api/v2/machine/recorder/rooms/{roomId}/games/{gameNumber}` | Принимает от рекордера отметки хода съёмки. | `hasRole('RECORDER')` | 3 |
| `EgressWebhookController` | `/api/v2/machine/webhooks/livekit-egress` | Принимает уведомление LiveKit о выгрузке записи. | `hasRole('EGRESS')` (`EgressWebhookFilter`: наш HMAC + `signing_key` LiveKit) | 1 |
| `MaintenanceSweepController` | `/api/v2/machine/maintenance` | Выполняет плановую уборку по расписанию. | `hasRole('CRON')` (`CronSecretFilter`) | 3 |
| `UsageSnapshotAgentController` | `/api/v2/machine/usage-snapshots` | Принимает снимок расхода от агента мониторинга. | `hasRole('MONITOR_AGENT')` (`MonitorSecretFilter`) | 1 |

Админских дублей уборки нет: `admin.js` не зовёт `/api/cleanup-*` ни разу, а `POST /api/cleanup-rooms`
из клиента растворился в `LeaveRoomUseCase`. Из четырёх пар «один use-case — два адреса», за которые
критиковали победившее дерево, осталась одна — снимок расхода (`admin.js:237` действительно жмёт
«снять сейчас»), и она оправдана двумя схемами входа.

### 4.12 `/api/v2/app` — 2 класса, 3 эндпоинта

| Класс | Путь | Ответственность | `@PreAuthorize` | Эп. |
|---|---|---|---|---|
| `AppEnvironmentController` | `/api/v2/app` | Отдаёт открытые сведения о среде приложения. | `permitAll` | 2 |
| `AnalyticsEventController` | `/api/v2/app/analytics-events` | Принимает событие клиентской аналитики. | `hasRole('USER')` | 1 |

### 4.13 Самопроверка

- **Двух схем прав на класс нет ни у одного из 74 HTTP-классов.** Проверено перечислением: каждая строка
  таблиц выше несёт ровно один предикат. Десять дефектных классов прошлого круга разошлись структурно —
  `MatchLifecycle`/`MatchParticipation`/`MatchState` сидят на одном базовом пути именно потому, что у них
  `isHost` / `isMember` / `isMemberOrSpectator`; так же разведены `TurnController` (`isExplainer`) и
  `TurnClockController` (`isMember`), `RoomHostController` и `MyRoomSeatController`,
  `RoomInviteStatusController` и `RoomInviteAcceptanceController`.
- **Фраз с союзом «и» нет.** Проверено перечислением; «Ведёт диверсионные действия игрока в партии» —
  одно действие над одним ресурсом (арсенал расходуется, событие пишется, клип живёт внутри того же
  сценария), а не перечисление.
- **Ветвлений по строковому аргументу в контроллерах нет.** Единственный оставшийся закрытый набор —
  `WeaponType` в теле `POST …/sabotages`: тринадцать типов проходят один конвейер (фаза → кулдаун →
  списание патрона → блокировка канала → событие) и различаются параметрами реестра, а не сценарием
  (`GameServiceImpl:986-1180`). Диспетчер — `Map<WeaponType, WeaponHandler>` внутри домена; в контроллере
  `switch` нет, значение проверяет Jackson до входа в метод.
- **Классов с одним эндпоинтом — 18** из 74. Список и оценка — §13, риск 5.

---

## 5. Полная таблица эндпоинтов

190 HTTP-операций в четырнадцати областях плюс 7 каналов и 3 служебных кадра `/ws/v2`.
Пометка **`db`** в столбце «заменяет» означает, что сегодня операцию выполняет браузер через
`POST /api/db/write` либо прямым `setDoc`/`updateDoc`/`deleteDoc`/`runTransaction`/`writeBatch`.
Таких строк 38.

### 5.1 `/api/v2/auth` (13)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v2/auth/sessions` | `POST /api/auth/login` | `OpenSessionRequestDTO` | `IssuedSessionResponseDTO` | 201 | `OpenSessionUseCase` | permitAll + лимит (email, IP) |
| POST | `/api/v2/auth/sessions/guest` | `POST /api/auth/guest` | `OpenGuestSessionRequestDTO` | `IssuedGuestSessionResponseDTO` | 201 | `OpenGuestSessionUseCase` | permitAll; токен несёт `ROLE_GUEST` (A7) |
| POST | `/api/v2/auth/sessions/renewal` | `POST /api/auth/refresh` | `RenewSessionRequestDTO` | `RenewedSessionResponseDTO` | 200 | `RenewSessionUseCase` | предъявленный refresh; реюз гасит цепочку (A11) |
| DELETE | `/api/v2/auth/sessions/current` | `POST /api/auth/logout` | `RevokeSessionRequestDTO` | — | 204 | `RevokeSessionUseCase` | предъявленный refresh |
| POST | `/api/v2/auth/accounts` | `POST /api/auth/register` | `RegisterAccountRequestDTO` | `RegisteredAccountResponseDTO` | 201 | `RegisterAccountUseCase` | permitAll + лимит IP |
| GET | `/api/v2/auth/accounts/nickname-availability` | `GET /api/nickname-available` (`app-core.js:7601`) | `NicknameAvailabilityQueryDTO` | `NicknameAvailabilityResponseDTO` | 200 | `CheckNicknameAvailabilityUseCase` | permitAll + лимит IP (A10) |
| POST | `/api/v2/auth/accounts/upgrades` | `POST /api/auth/link` | `UpgradeGuestAccountRequestDTO` | `UpgradedGuestAccountResponseDTO` | 200 | `UpgradeGuestAccountUseCase` | `hasRole('GUEST')`; `uid` из токена, не из тела (A4) |
| POST | `/api/v2/auth/password-resets` | `POST /api/auth/password-reset` | `RequestPasswordResetRequestDTO` | `PasswordResetAcceptedResponseDTO` | 202 | `RequestPasswordResetUseCase` | permitAll + лимит; ссылка уходит письмом (A3) |
| POST | `/api/v2/auth/password-resets/completions` | `POST /api/auth/password-reset/confirm` | `CompletePasswordResetRequestDTO` | — | 204 | `CompletePasswordResetUseCase` | токен из письма |
| GET | `/api/v2/auth/me` | `GET /api/auth/me` (учётная часть) | — | `MyAccountResponseDTO` | 200 | `GetMyAccountUseCase` | `hasAnyRole('USER','GUEST')` |
| GET | `/api/v2/auth/me/ban-state` | **`db`** подписка `bans/{uid}` (`realtime-social.js:113`, `live-preview.js:99`) | — | `MyBanStateResponseDTO` | 200 | `GetMyBanStateUseCase` | `hasAnyRole('USER','GUEST')` |
| PUT | `/api/v2/auth/me/security/password` | `POST /api/auth/password` | `ChangeOwnPasswordRequestDTO` | — | 204 | `ChangeOwnPasswordUseCase` | `hasRole('USER')`; неверный текущий пароль → 403, не 401 (D3) |
| DELETE | `/api/v2/auth/me/security/sessions` | `POST /api/auth/logout-all` | — | `RevokedSessionsResponseDTO` | 200 | `RevokeAllSessionsUseCase` | `hasRole('USER')` |

### 5.2 `/api/v2/profile` (20)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v2/profile/me` | `portal ensure_profile` (профильная часть, 11 ключей) | — | `MyProfileResponseDTO` | 200 | `GetMyProfileUseCase` | `hasRole('USER')` |
| PUT | `/api/v2/profile/me/nickname` | `portal set_nickname`; `PATCH /api/auth/profile` | `ClaimNicknameRequestDTO` | `ClaimedNicknameResponseDTO` | 200 | `ClaimNicknameUseCase` | `hasRole('USER')`; 409 `NICKNAME_TAKEN` |
| PUT | `/api/v2/profile/me/avatar` | `portal set_avatar` (≤120000 симв.) | `ReplaceAvatarRequestDTO` | `StoredAvatarResponseDTO` | 200 | `ReplaceAvatarUseCase` | `hasRole('USER')` |
| PUT | `/api/v2/profile/me/ui-language` | `portal set_ui_language` | `ChangeUiLanguageRequestDTO` | `UiLanguageResponseDTO` | 200 | `ChangeUiLanguageUseCase` | `hasRole('USER')`; невалидное → 400, не молчаливая подмена |
| POST | `/api/v2/profile/me/nickname-requests` | `portal request_nickname` | `SubmitNicknameRequestDTO` | `SubmittedNicknameRequestResponseDTO` | 201 | `SubmitNicknameRequestUseCase` | `hasRole('USER')` |
| POST | `/api/v2/profile/me/onboarding` | цепочка `set_division → set_nickname → record_consent` (`app-core.js:7726-7728`) + **`db`** `setDoc users/{uid}` (`:7711`) | `CompleteOnboardingRequestDTO` | `CompletedOnboardingResponseDTO` | 200 | `CompleteOnboardingUseCase` | `hasRole('USER')`; одна транзакция |
| PUT | `/api/v2/profile/me/division` | `portal set_division` | `LockDivisionRequestDTO` | `LockedDivisionResponseDTO` | 200 | `LockDivisionUseCase` | `hasRole('USER')`; повтор → 409 `DIVISION_LOCKED` |
| PUT | `/api/v2/profile/presence/me` | `portal presence_ping`; **`db`** `updateDoc users.lastSeenAt` (`app-core.js:14704`) | — | `PresenceHeartbeatResponseDTO` | 200 | `TouchMyPresenceUseCase` | `hasRole('USER')` |
| GET | `/api/v2/profile/presence/summary` | `portal presence_summary` | — | `PresenceSummaryResponseDTO` | 200 | `CountOnlinePlayersUseCase` | `hasRole('USER')` |
| PUT | `/api/v2/profile/presence/me/room` | **`db`** `setDoc users/{uid}.activeRoomId` (`app-core.js:4519`) | `SetActiveRoomRequestDTO` | `ActiveRoomResponseDTO` | 200 | `SetMyActiveRoomUseCase` | `hasRole('USER')` |
| DELETE | `/api/v2/profile/presence/me/room` | **`db`** `setProfileActiveRoom(null)` (`app-core.js:4551`) | — | — | 204 | `ClearMyActiveRoomUseCase` | `hasRole('USER')` |
| GET | `/api/v2/profile/me/meme-loadout` | `ensure_profile` (`defaultMemeLoadout`, `loadoutCount`, `loadoutReady`) | — | `DefaultLoadoutResponseDTO` | 200 | `GetDefaultLoadoutUseCase` | `hasRole('USER')` |
| PUT | `/api/v2/profile/me/meme-loadout` | **`db`** `setDoc users/{uid}.defaultMemeLoadout` (`app-core.js:5078`, `:5107`, `:13698`) | `SaveDefaultLoadoutRequestDTO` | `SavedDefaultLoadoutResponseDTO` | 200 | `SaveDefaultLoadoutUseCase` | `hasRole('USER')`; ровно пять существующих мемов |
| GET | `/api/v2/profile/me/sabotage-entitlement` | вложенный блок `sabotage` в `ensure_profile` (`ProfileServiceImpl:446-456`), читает `home/home.js` | — | `SabotageEntitlementResponseDTO` | 200 | `GetSabotageEntitlementUseCase` | `hasRole('USER')` |
| POST | `/api/v2/profile/me/sabotage-entitlement/consumptions` | `portal consume_sabotage_game` (`app-core.js:12969`) | `ConsumeSabotageEntitlementRequestDTO` | `ConsumedSabotageEntitlementResponseDTO` | 200 | `ConsumeSabotageEntitlementUseCase` | `hasRole('USER')`; ключ идемпотентности строит сервер (E10); 402 `SABOTAGE_LIMIT_REACHED` |
| GET | `/api/v2/profile/me/consents` | поля согласий внутри `ensure_profile` | — | `MyConsentsResponseDTO` | 200 | `GetMyConsentsUseCase` | `hasAnyRole('USER','GUEST')` |
| POST | `/api/v2/profile/me/consents` | `portal record_consent` | `RecordConsentRequestDTO` | `RecordedConsentResponseDTO` | 201 | `RecordConsentUseCase` | `hasAnyRole('USER','GUEST')` |
| GET | `/api/v2/profile/players/{uid}` | `portal public_player_profile` | — | `PublicPlayerResponseDTO` | 200 | `GetPublicPlayerUseCase` | `hasRole('USER')`; e-mail не отдаётся |
| GET | `/api/v2/profile/divisions` | список девяти языков, продублированный в `Divisions`, `DocumentAccessGuard:48` и во фронте | — | `DivisionCatalogResponseDTO` | 200 | `ListDivisionsUseCase` | permitAll |
| GET | `/api/v2/profile/divisions/suggestion` | `GET /api/geo` (`app-core.js:834`) | `DivisionSuggestionQueryDTO` | `DivisionSuggestionResponseDTO` | 200 | `SuggestDivisionUseCase` | permitAll; заголовки `CF-IPCountry`/`X-Country-Code` описаны `@Parameter` |

### 5.3 `/api/v2/friends` (9)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v2/friends` | `portal friends` (массив друзей; N+1 на 233 запроса — B8) | `MyFriendsQueryDTO` | `MyFriendsPageResponseDTO` | 200 | `ListMyFriendsUseCase` | `hasRole('USER')` |
| GET | `/api/v2/friends/presence` | `portal friends` (массив `statuses`) | — | `FriendPresenceResponseDTO` | 200 | `ListFriendPresenceUseCase` | `hasRole('USER')` |
| DELETE | `/api/v2/friends/{friendUid}` | `portal remove_friend` | — | — | 204 | `RemoveFriendUseCase` | `hasRole('USER')`; 409 `TEAMMATE_MUST_REMAIN_FRIEND` |
| POST | `/api/v2/friends/requests/by-nickname` | `portal add_friend` с `nickname` (`friends/friends.js:23`) | `InviteFriendByNicknameRequestDTO` | `SentFriendRequestResponseDTO` | 201 | `InviteFriendByNicknameUseCase` | `hasRole('USER')`; 400 `FRIEND_SELF` |
| POST | `/api/v2/friends/requests/by-player` | `portal add_friend` с `friendUid` (`app-core.js:14618`) | `InviteFriendByUidRequestDTO` | `SentFriendRequestByUidResponseDTO` | 201 | `InviteFriendByUidUseCase` | `hasRole('USER')` |
| GET | `/api/v2/friends/requests/incoming` | `portal friends` (`incoming`) + подписка `realtime-social.js:108` | — | `IncomingFriendRequestsResponseDTO` | 200 | `ListIncomingFriendRequestsUseCase` | `hasRole('USER')` |
| GET | `/api/v2/friends/requests/outgoing` | `portal friends` (`outgoing`) + подписка `realtime-social.js:110` | — | `OutgoingFriendRequestsResponseDTO` | 200 | `ListOutgoingFriendRequestsUseCase` | `hasRole('USER')` |
| POST | `/api/v2/friends/requests/{requestId}/acceptance` | `portal answer_friend` с `accept=true` | — | `AcceptedFriendRequestResponseDTO` | 200 | `AcceptFriendRequestUseCase` | `@friendAuthz.isRecipient` |
| POST | `/api/v2/friends/requests/{requestId}/rejection` | `portal answer_friend` с `accept=false` | — | — | 204 | `DeclineFriendRequestUseCase` | `@friendAuthz.isRecipient` |

### 5.4 `/api/v2/chat` (7)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v2/chat/threads` | `portal social_threads` | — | `ChatThreadsResponseDTO` | 200 | `ListMyChatThreadsUseCase` | `hasRole('USER')` |
| GET | `/api/v2/chat/inbox` | **`db`** подписка `socialInboxes/{uid}` (`realtime-social.js:111`) | — | `SocialInboxResponseDTO` | 200 | `GetMySocialInboxUseCase` | `hasRole('USER')` |
| GET | `/api/v2/chat/{peerUid}/messages` | `portal get_chat` + **`db`** подписка `directChats/{pair}/messages` (`portal.js:121`, `friends.js:20`) | `ChatHistoryQueryDTO` | `ChatHistoryPageResponseDTO` | 200 | `ReadChatHistoryUseCase` | `@chatAuthz.areFriends`; 403 `FRIEND_REQUIRED` |
| POST | `/api/v2/chat/{peerUid}/messages` | `portal send_chat` без вложения | `SendChatTextRequestDTO` | `SentChatTextResponseDTO` | 201 | `SendChatTextUseCase` | `@chatAuthz.areFriends` |
| POST | `/api/v2/chat/{peerUid}/image-messages` | `portal send_chat` с `attachment.kind=image` | `SendChatImageRequestDTO` | `SentChatImageResponseDTO` | 201 | `SendChatImageUseCase` | `@chatAuthz.areFriends` |
| POST | `/api/v2/chat/{peerUid}/recording-messages` | `portal send_chat` с `attachment.kind=recording` | `ShareRecordingInChatRequestDTO` | `SharedRecordingInChatResponseDTO` | 201 | `ShareRecordingInChatUseCase` | `@chatAuthz.areFriends`; 403 `RECORDING_NOT_SAVED` |
| PUT | `/api/v2/chat/{peerUid}/read-mark` | `portal mark_chat_read` | — | — | 204 | `MarkChatReadUseCase` | `@chatAuthz.areFriends` |

### 5.5 `/api/v2/team` (12)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v2/team` | `portal create_team` | `FoundTeamRequestDTO` | `FoundedTeamResponseDTO` | 201 | `FoundTeamUseCase` | `hasRole('USER')`; 409 `ALREADY_IN_TEAM` и семь других кодов |
| GET | `/api/v2/team/invites` | `portal my_team` (массив `invites`, `TeamServiceImpl:133-136`) | — | `TeamInvitesResponseDTO` | 200 | `ListMyTeamInvitesUseCase` | `hasRole('USER')` |
| POST | `/api/v2/team/invites/{inviteId}/acceptance` | `portal accept_team` | — | `AcceptedTeamInviteResponseDTO` | 200 | `AcceptTeamInviteUseCase` | `@teamInviteAuthz.isInvitee` |
| POST | `/api/v2/team/invites/{inviteId}/rejection` | `portal decline_team` | — | — | 204 | `DeclineTeamInviteUseCase` | `@teamInviteAuthz.isInvitee` |
| GET | `/api/v2/team/me` | `portal my_team` (`team`, `partner`, `stats`, `divisionBadge`) | — | `MyTeamResponseDTO` | 200 | `GetMyTeamUseCase` | `@teamAuthz.isMember` |
| POST | `/api/v2/team/me/lobby` | `portal ensure_team_lobby` | — | `TeamLobbyResponseDTO` | 200 | `EnsureTeamLobbyUseCase` | `@teamAuthz.isMember`; комната через `RoomCommandPort` в одной транзакции |
| GET | `/api/v2/team/{teamId}` | `portal public_team_profile` (4 уровня вложенности) | — | `PublicTeamResponseDTO` | 200 | `GetPublicTeamUseCase` | `hasRole('USER')`; рейтинги через `RankingPort` |
| POST | `/api/v2/team/me/preflight` | `portal team_preflight_start` (21 расплющенный ключ → вложенный record) | `StartPreflightRequestDTO` | `PreflightSessionResponseDTO` | 201 | `StartPreflightUseCase` | `@teamAuthz.isActiveMember` |
| GET | `/api/v2/team/me/preflight` | `portal team_preflight_status` + подписка `portal.js:125` | — | `PreflightStatusResponseDTO` | 200 | `GetPreflightSessionUseCase` | `@teamAuthz.isActiveMember` |
| DELETE | `/api/v2/team/me/preflight` | `portal team_preflight_cancel` | — | — | 204 | `CancelPreflightUseCase` | `@teamAuthz.isActiveMember` |
| PUT | `/api/v2/team/me/preflight/participants/me/media-check` | `portal team_preflight_media` | `ReportPreflightMediaRequestDTO` | `PreflightMediaResponseDTO` | 200 | `ReportPreflightMediaUseCase` | `@preflightAuthz.isParticipant`; `teamId` из токена; TTL 25 с |
| PUT | `/api/v2/team/me/preflight/participants/me/readiness` | `portal team_preflight_ready` | `SetPreflightReadinessRequestDTO` | `PreflightReadinessResponseDTO` | 200 | `SetPreflightReadinessUseCase` | `@preflightAuthz.isParticipant`; 409 `MEDIA_NOT_READY` |

### 5.6 `/api/v2/lobby` (10)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v2/lobby/rooms` | **`db`** подписка `query(rooms, where phase in […])` (`home/home.js:93`) | `LobbyRoomQueryDTO` | `LobbyRoomPageResponseDTO` | 200 | `ListOpenRoomsUseCase` | `hasAnyRole('GUEST','USER')`; приватные и чужие тестовые отсекает сервер |
| GET | `/api/v2/lobby/rooms/{roomId}` | **`db`** три подписки состава выбранной комнаты (`home/home.js:88`) + подписка `rooms/{id}/players/{uid}` (`live-preview.js:34`) | — | `LobbyRoomPreviewResponseDTO` | 200 | `GetRoomPreviewUseCase` | `hasAnyRole('GUEST','USER')`; только публичные поля |
| POST | `/api/v2/lobby/rooms/{roomId}/preview-session` | **`db`** `setDoc spectators/{uid}` с `preview:true` (`live-preview.js:101`) + `POST /api/token` с `preview_session` (`:104`) | `OpenRoomPreviewRequestDTO` | `RoomPreviewSessionResponseDTO` | 201 | `OpenRoomPreviewUseCase` | `hasAnyRole('GUEST','USER')`; токен только на приём |
| PUT | `/api/v2/lobby/rooms/{roomId}/preview-session` | **`db`** `updateDoc spectators.lastSeenAt` в превью (`live-preview.js:102`) | — | `RoomPreviewHeartbeatResponseDTO` | 200 | `TouchRoomPreviewUseCase` | `hasAnyRole('GUEST','USER')`; 403 `BANNED` закрывает превью |
| DELETE | `/api/v2/lobby/rooms/{roomId}/preview-session` | **`db`** `deleteDoc spectators/{uid}` при уходе из превью (`app-core.js:4852`) | — | — | 204 | `CloseRoomPreviewUseCase` | `hasAnyRole('GUEST','USER')` |
| POST | `/api/v2/lobby/tickets/casual` | `portal matchmake` с `ranked=false` (`home/home.js:99`) | `OpenCasualTicketRequestDTO` | `MatchTicketResponseDTO` | 201 | `OpenCasualTicketUseCase` | `hasRole('USER')` |
| GET | `/api/v2/lobby/tickets/{ticketId}` | повторный вызов `matchmake` ради опроса (раз в 3-5 с) | — | `MatchTicketStatusResponseDTO` | 200 | `GetMyMatchTicketUseCase` | `hasRole('USER')`; чужой билет → 404 |
| DELETE | `/api/v2/lobby/tickets/{ticketId}` | `portal cancel_matchmake` (сегодня владение не проверяется) | — | — | 204 | `CancelMatchTicketUseCase` | `hasRole('USER')` |
| POST | `/api/v2/lobby/ranked/tickets` | `portal matchmake` с `ranked=true` (`portal.js:127`) | `OpenRankedTicketRequestDTO` | `RankedTicketResponseDTO` | 201 | `OpenRankedTicketUseCase` | `@teamAuthz.isCaptain`; требует пройденного префлайта |
| POST | `/api/v2/lobby/ranked/rooms/{roomId}/entry` | `portal ranked_join_room` | `JoinRankedRoomRequestDTO` | `RankedRoomEntryResponseDTO` | 200 | `JoinRankedRoomUseCase` | `@teamAuthz.isCaptain` |

### 5.7 `/api/v2/room` (34)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v2/room` | **`db`** `writeBatch createRoom()` (`app-core.js:12101-12120`) | `CreateRoomRequestDTO` | `CreatedRoomResponseDTO` | 201 | `CreateRoomUseCase` | `hasRole('USER')`; `id` и владение ставит сервер (A1) |
| PUT | `/api/v2/room/{roomId}/players/me` | **`db`** `setDoc rooms/{id}/players/{uid}` (`app-core.js:12277`), починка игрока (`:4634`) | `EnterRoomRequestDTO` | `RoomSeatResponseDTO` | 200 | `EnterRoomUseCase` | `hasRole('USER')`; дивизион, приватность, вместимость, бан |
| PUT | `/api/v2/room/{roomId}/spectators/me` | **`db`** `setDoc rooms/{id}/spectators/{uid}` (`app-core.js:12373`) | `TakeSpectatorSeatRequestDTO` | `SpectatorSeatResponseDTO` | 200 | `TakeSpectatorSeatUseCase` | `hasRole('USER')`; приватная комната → 403 |
| GET | `/api/v2/room/{roomId}` | **`db`** `GET /api/db/document?path=rooms/{id}` (`:8634`, `:12281`) + подписки players/teams/spectators (`:8750`, `:8761`, `:8806`) одним композитным ответом | — | `RoomSnapshotResponseDTO` | 200 | `GetRoomSnapshotUseCase` | `@roomAuthz.isMemberOrSpectator` |
| DELETE | `/api/v2/room/{roomId}/players/me` | **`db`** `deleteDoc(playerRef)` (`app-core.js:12290`) + `POST /api/cleanup-rooms?room_id=` (`:1045-1051`) | — | `LeftRoomResponseDTO` (`roomClosed`) | 200 | `LeaveRoomUseCase` | `@roomAuthz.isMember`; уборка опустевшей комнаты — инвариант use-case |
| PUT | `/api/v2/room/{roomId}/players/me/devices` | **`db`** `updateDoc players{camera,microphone,mediaReadyAt}` (`:5093`, `:12499`, `livekit.js:3175`) | `ReportDeviceStateRequestDTO` | `DeviceStateResponseDTO` | 200 | `ReportDeviceStateUseCase` | `@roomAuthz.isMember` |
| PUT | `/api/v2/room/{roomId}/players/me/heartbeat` | **`db`** `updateDoc players.lastSeenAt` (`app-core.js:8333`, `:14700`) | — | `RoomSeatHeartbeatResponseDTO` | 200 | `TouchRoomSeatUseCase` | `@roomAuthz.isMember`; чистое обновление отметки, без побочных передач |
| POST | `/api/v2/room/{roomId}/players/me/video-token` | `POST /api/token` с `role=player` (`livekit.js:541`) | `IssuePlayerVideoTokenRequestDTO` | `PlayerVideoTokenResponseDTO` | 201 | `IssuePlayerVideoTokenUseCase` | `@roomAuthz.isMember`; `turn{}` — вложенный блок ответа |
| DELETE | `/api/v2/room/{roomId}/spectators/me` | **`db`** `deleteDoc spectators/{uid}` (`app-core.js:12423`) | — | — | 204 | `LeaveSpectatorSeatUseCase` | `@roomAuthz.isSpectator` |
| PUT | `/api/v2/room/{roomId}/spectators/me/heartbeat` | **`db`** `updateDoc spectators.lastSeenAt` (`livekit.js:3161`) | — | `SpectatorHeartbeatResponseDTO` | 200 | `TouchSpectatorSeatUseCase` | `@roomAuthz.isSpectator` |
| POST | `/api/v2/room/{roomId}/spectators/me/video-token` | `POST /api/token` с `role=spectator` | `IssueSpectatorVideoTokenRequestDTO` | `SpectatorVideoTokenResponseDTO` | 201 | `IssueSpectatorVideoTokenUseCase` | `@roomAuthz.isSpectator` |
| POST | `/api/v2/room/{roomId}/teams` | **`db`** `runTransaction addTeam()` (`app-core.js:12626`) | `CreateRoomTeamRequestDTO` | `RoomTeamResponseDTO` | 201 | `CreateRoomTeamUseCase` | `@roomAuthz.isMember`; фаза набора |
| DELETE | `/api/v2/room/{roomId}/teams/{teamId}` | **`db`** `runTransaction deleteTeam()` (`app-core.js:12656`) | — | — | 204 | `DeleteRoomTeamUseCase` | `@roomAuthz.isMember`; команда пуста |
| PUT | `/api/v2/room/{roomId}/teams/{teamId}/members/me` | **`db`** `runTransaction joinTeam()` (`app-core.js:12511`), `updateDoc teamId` (`:12173`, `:12185`) | — | `RoomTeamMembershipResponseDTO` | 200 | `JoinRoomTeamUseCase` | `@roomAuthz.isMember` |
| DELETE | `/api/v2/room/{roomId}/teams/{teamId}/members/me` | **`db`** `runTransaction leaveTeam()` (`app-core.js:12576`) | — | — | 204 | `LeaveRoomTeamUseCase` | `@roomAuthz.isMember` |
| POST | `/api/v2/room/{roomId}/teams/draw` | `game randomize_teams` (`app-core.js:4869`) | — | `RoomTeamDrawResponseDTO` | 200 | `DrawRoomTeamsUseCase` | `@roomAuthz.isMember`; кнопка намеренно не хозяйская (`:11843`) |
| GET | `/api/v2/room/{roomId}/chat-messages` | **`db`** чтение `rooms/{id}/chat` + подписка (`app-core.js:8795`) | `RoomChatQueryDTO` | `RoomChatPageResponseDTO` | 200 | `ReadRoomChatUseCase` | `@roomAuthz.isMemberOrSpectator` |
| POST | `/api/v2/room/{roomId}/chat-messages` | **`db`** `setDoc rooms/{id}/chat/{id}` (`app-core.js:8478`) | `PostRoomChatMessageRequestDTO` | `RoomChatMessageResponseDTO` | 201 | `PostRoomChatMessageUseCase` | `@roomAuthz.isMemberOrSpectator`; автора ставит сервер (A1) |
| POST | `/api/v2/room/{roomId}/chat-images` | **`db`** `setDoc` картинки в чат комнаты (`app-core.js:8473`) | `PostRoomChatImageRequestDTO` | `RoomChatImageResponseDTO` | 201 | `PostRoomChatImageUseCase` | `@roomAuthz.isMemberOrSpectator` |
| PATCH | `/api/v2/room/{roomId}/chat-messages/{messageId}` | **`db`** правка сообщения (`app-core.js:8540`) | `EditRoomChatMessageRequestDTO` | `EditedRoomChatMessageResponseDTO` | 200 | `EditRoomChatMessageUseCase` | `@roomAuthz.isMemberOrSpectator`; авторство — инвариант use-case |
| PUT | `/api/v2/room/{roomId}/name` | **`db`** `updateDoc(roomRef,{name})` (`app-core.js:12082`) | `RenameRoomRequestDTO` | `RoomNameResponseDTO` | 200 | `RenameRoomUseCase` | `@roomAuthz.isHost` |
| PUT | `/api/v2/room/{roomId}/turn-duration` | **`db`** `setTurnDuration` (`app-core.js:12723`) | `SetTurnDurationRequestDTO` | `TurnDurationResponseDTO` | 200 | `SetTurnDurationUseCase` | `@roomAuthz.isHost` |
| DELETE | `/api/v2/room/{roomId}` | `POST /api/cleanup-rooms?room_id=` от хозяина (`app-core.js:1048`) | — | `ClosedRoomResponseDTO` | 200 | `CloseRoomUseCase` | `@roomAuthz.isHost` |
| POST | `/api/v2/room/{roomId}/reset` | **`db`** `resetRoom()` (`app-core.js:13570`) + `runTransaction backToSetup()` (`:13508`) | — | `RoomSetupResponseDTO` | 200 | `ResetRoomUseCase` | `@roomAuthz.isHost`; две клиентские процедуры сведены в одну |
| PUT | `/api/v2/room/{roomId}/host` | `game manual_host_transfer` (`app-core.js:990`) | `TransferHostRequestDTO` | `RoomHostResponseDTO` | 200 | `TransferHostUseCase` | `@roomAuthz.isHost`; цель активна ≤ 5 мин |
| PUT | `/api/v2/room/{roomId}/host/heartbeat` | `game setup_host_activity` (`app-core.js:996`) | `ReportHostActivityRequestDTO` | `HostActivityResponseDTO` | 200 | `ReportHostActivityUseCase` | `@roomAuthz.isHost`; `kind` — enum, не свободная строка |
| PUT | `/api/v2/room/{roomId}/public-presence` | **`db`** `updateDoc {publicActivePlayers, publicPresenceAt}` (`app-core.js:11931`) | `ReportPublicPresenceRequestDTO` | `PublicPresenceResponseDTO` | 200 | `ReportRoomPublicPresenceUseCase` | `@roomAuthz.isHost`; питает счётчик лобби (`home/home.js:69`) |
| POST | `/api/v2/room/{roomId}/host/handover` | `game setup_host_watch` (`app-core.js:1016`) | — | `HostHandoverResponseDTO` (`outcome`, `newHostUid`, `newHostName`, `remainingMs`, `activePlayers`) | 200 | `HandOverIdleHostUseCase` | `@roomAuthz.isMember` (A6); пять форм ответа сведены к одной |
| DELETE | `/api/v2/room/{roomId}/players/{uid}` | `game kick_player` (`app-core.js:9024`) | — | — | 204 | `EjectPlayerUseCase` | `@roomAuthz.isHost`; 409 `KICK_SELF_FORBIDDEN` |
| POST | `/api/v2/room/{roomId}/spectators/{uid}/promotion` | `portal promote_room_spectator` (`app-core.js:4837`) | — | `PromotedSpectatorResponseDTO` | 200 | `PromoteSpectatorUseCase` | `@roomAuthz.isHost`; фаза набора |
| POST | `/api/v2/room/{roomId}/invites` | `portal send_room_invite` (`app-core.js:4798`) | `SendRoomInviteRequestDTO` | `SentRoomInviteResponseDTO` | 201 | `SendRoomInviteUseCase` | `@roomAuthz.isHost`; ветка «уже внутри» — поле ответа |
| GET | `/api/v2/room/invites` | `portal room_invite_statuses` (до 60 id, `realtime-social.js:71-77`) | `RoomInviteStatusQueryDTO` | `RoomInviteStatusesResponseDTO` | 200 | `ReadRoomInviteStatusesUseCase` | `hasRole('USER')`; видимость режется построчно в проекции |
| POST | `/api/v2/room/invites/{inviteId}/acceptance` | `portal accept_room_invite` (`app-core.js:4818`) | — | `AcceptedRoomInviteResponseDTO` | 200 | `AcceptRoomInviteUseCase` | `@roomInviteAuthz.isRecipient`; 410 `ROOM_INVITE_EXPIRED` |
| PUT | `/api/v2/room/{roomId}/recording-preference` | `game set_recording_preference` (`app-core.js:12737`) | `SetRecordingPreferenceRequestDTO` | `RecordingPreferenceResponseDTO` | 200 | `SetRecordingPreferenceUseCase` | `hasRole('OWNER')`; сегодня — проверка по e-mail внутри сервиса (F7) |

### 5.8 `/api/v2/game` (26)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v2/game/{roomId}` | `game prepare_game` (`app-core.js:12947`) + **`db`** `runTransaction collectWordsAndStart()` (`:12768`) | `StartMatchRequestDTO` | `StartedMatchResponseDTO` | 201 | `StartMatchUseCase` | `@roomAuthz.isHost` |
| PUT | `/api/v2/game/{roomId}/pause` | `game toggle_pause` с `paused=true` (`app-core.js:12388`) | — | `MatchPausedResponseDTO` | 200 | `PauseMatchUseCase` | `@roomAuthz.isHost` |
| DELETE | `/api/v2/game/{roomId}/pause` | `game toggle_pause` с `paused=false` | — | `MatchResumedResponseDTO` | 200 | `ResumeMatchUseCase` | `@roomAuthz.isHost` |
| POST | `/api/v2/game/{roomId}/ranked-autostart` | `game ranked_autostart` (`app-core.js:1035`) | — | `RankedAutostartResponseDTO` | 200 | `AutostartRankedMatchUseCase` | `@roomAuthz.isMember`; три формы ответа → одна с `outcome` |
| PUT | `/api/v2/game/{roomId}/word-submissions/me` | **`db`** `runTransaction saveWords()` (`app-core.js:12686`) | `SubmitWordsRequestDTO` | `SubmittedWordsResponseDTO` | 200 | `SubmitWordsUseCase` | `@roomAuthz.isMember`; чужие слова не читает никто |
| PUT | `/api/v2/game/{roomId}/players/me/heartbeat` | `game sync_game_presence` (обычные причины) | — | `MatchPresenceResponseDTO` | 200 | `ReconcileMatchPresenceUseCase` | `@roomAuthz.isMember`; сверка ростера, пауза, техзавершение — здесь |
| POST | `/api/v2/game/{roomId}/players/me/departure` | `game sync_game_presence` с `browser-pagehide` или `livekit-reconnecting` (`GameServiceImpl:630-631`) | — | `MatchDepartureResponseDTO` | 200 | `ReportDepartureUseCase` | `@roomAuthz.isMember`; помечает ушедшего до опроса LiveKit; комнату не закрывает |
| POST | `/api/v2/game/{roomId}/players/me/disconnection` | `game sync_game_presence` с `livekit-disconnected` (`GameServiceImpl:676-686`) | — | `MatchDisconnectionResponseDTO` | 200 | `ReportDisconnectionUseCase` | `@roomAuthz.isMember`; единственная ветка, вправе закрыть комнату |
| GET | `/api/v2/game/{roomId}` | **`db`** игровые поля `rooms/{id}` через документ и подписку | — | `MatchStateResponseDTO` | 200 | `GetMatchStateUseCase` | `@roomAuthz.isMemberOrSpectator`; `currentWord` виден только объясняющему |
| POST | `/api/v2/game/{roomId}/turn` | **`db`** `runTransaction beginTurn()` (`app-core.js:12987`) | — | `TurnStartedResponseDTO` | 201 | `BeginTurnUseCase` | `@gameAuthz.isExplainer`; слово тянет сервер, `drawRandomWord(bag)` удаляется |
| POST | `/api/v2/game/{roomId}/turn/words/{wordId}/guess` | **`db`** `writeBatch` + `runTransaction guessed()` (`:13327`, `:13339`) | `GuessWordRequestDTO` | `GuessedWordResponseDTO` | 200 | `GuessWordUseCase` | `@gameAuthz.isExplainer`; очки начисляет сервер |
| POST | `/api/v2/game/{roomId}/turn/words/{wordId}/skip` | **`db`** `writeBatch` + `runTransaction skipWord()` (`:13404`, `:13409`) | `SkipWordRequestDTO` | `SkippedWordResponseDTO` | 200 | `SkipWordUseCase` | `@gameAuthz.isExplainer` |
| POST | `/api/v2/game/{roomId}/turn/completion` | **`db`** `runTransaction endTurn()` (`app-core.js:13456`) | `CompleteTurnRequestDTO` (`turnId`) | `TurnCompletedResponseDTO` | 200 | `CompleteTurnUseCase` | `@gameAuthz.isExplainer`; идемпотентно по `turnId` |
| POST | `/api/v2/game/{roomId}/turn/expiry` | **`db`** `ensureExhaustedTurnAdvanced()` (`app-core.js:13300`) | `ExpireTurnRequestDTO` (`turnId`) | `TurnExpiredResponseDTO` | 200 | `ExpireTurnUseCase` | `@roomAuthz.isMember`; время считает `Clock` сервера |
| POST | `/api/v2/game/{roomId}/turn/next` | **`db`** `runTransaction nextTurn()` (`app-core.js:13469`) | `AdvanceTurnRequestDTO` (`turnId`) | `NextTurnResponseDTO` | 200 | `AdvanceTurnUseCase` | `@roomAuthz.isMember`; порядок команд считает сервер |
| PUT | `/api/v2/game/{roomId}/appeal/votes/{wordId}` | `game appeal_vote` (`app-core.js:9529`) | `CastAppealVoteRequestDTO` | `AppealVotesResponseDTO` | 200 | `CastAppealVoteUseCase` | `@gameAuthz.isPlayer`; «не своя команда» — правило внутри use-case |
| POST | `/api/v2/game/{roomId}/appeal/closing` | `game finalize_appeal` (`app-core.js:9537`) | `CloseAppealRequestDTO` (`turnId`) | `AppealResultResponseDTO` | 200 | `CloseAppealUseCase` | `@gameAuthz.isPlayer`; идемпотентно по `turnId` |
| GET | `/api/v2/game/{roomId}/players/me/arsenal` | **`db`** поле `arsenal` в подписке `rooms/{id}/players/{uid}` | — | `ArsenalResponseDTO` | 200 | `GetMyArsenalUseCase` | `@gameAuthz.isPlayer` |
| PUT | `/api/v2/game/{roomId}/players/me/loadout` | **`db`** `updateDoc {memeLoadout}` при входе в партию (`app-core.js:12342`) | `SetMatchLoadoutRequestDTO` | `MatchLoadoutResponseDTO` | 200 | `SetMatchLoadoutUseCase` | `@gameAuthz.isPlayer` |
| PUT | `/api/v2/game/{roomId}/players/me/loadout/slots/{slotIndex}` | `game replace_meme_slot` (`app-core.js:9683`) | `ReplaceLoadoutSlotRequestDTO` | `ReplacedLoadoutSlotResponseDTO` | 200 | `ReplaceLoadoutSlotUseCase` | `@gameAuthz.isPlayer`; `slotIndex` 0..4 |
| POST | `/api/v2/game/{roomId}/sabotages` | `game sabotage` (13 значений `type`, 13+ точек правки — F2) | `UseWeaponRequestDTO` (`enum WeaponType`) | `SabotageEventResponseDTO` | 201 | `UseWeaponUseCase` | `@gameAuthz.isPlayer`; диспетчер — `Map<WeaponType, WeaponHandler>` в домене |
| POST | `/api/v2/game/{roomId}/replacement-clips` | `game replacement_record` (`app-core.js:3975`) | `OrderReplacementClipRequestDTO` | `ReplacementClipResponseDTO` | 201 | `OrderReplacementClipUseCase` | `@gameAuthz.isPlayer`; ≥ 11 с хода, лимит 3 |
| PUT | `/api/v2/game/{roomId}/replacement-clips/{clipId}/result` | `game replacement_record_result` (`app-core.js:3283`) | `ReportClipResultRequestDTO` | `ReportedClipResultResponseDTO` | 200 | `ReportClipResultUseCase` | `@gameAuthz.isPlayer`; «тот, кого снимали» — инвариант use-case |
| DELETE | `/api/v2/game/{roomId}/replacement-clips/{clipId}` | `game replacement_discard` (`app-core.js:4003`) | — | — | 204 | `DiscardReplacementClipUseCase` | `@gameAuthz.isPlayer`; авторство — инвариант use-case |
| GET | `/api/v2/game/weapons` | собственная копия арсенала во фронте (`app-core.js:356 BASE_ARSENAL`) и три расходящихся литерала на сервере (F2) | — | `WeaponCatalogResponseDTO` | 200 | `GetWeaponCatalogUseCase` | `hasRole('USER')` |
| GET | `/api/v2/game/clock` | **`db`** калибровка через `updateDoc(clockProbeAt)` + `getDocFromServer` (`app-core.js:8053-8062`) | — | `ServerClockResponseDTO` | 200 | `ReadServerClockUseCase` | permitAll |

### 5.9 `/api/v2/rating` (4)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v2/rating/teams` | `portal ratings` (массив `teams`) | `TeamRankingQueryDTO` | `TeamRankingPageResponseDTO` | 200 | `ListTeamRankingUseCase` | `hasRole('USER')`; дефолты `mode/season/year/division` объявлены явно |
| GET | `/api/v2/rating/players` | `portal ratings` (массив `players`) | `PlayerRankingQueryDTO` | `PlayerRankingPageResponseDTO` | 200 | `ListPlayerRankingUseCase` | `hasRole('USER')` |
| GET | `/api/v2/rating/champion` | `portal ratings` (объект `champion`) | `SeasonChampionQueryDTO` | `SeasonChampionResponseDTO` | 200 | `GetSeasonChampionUseCase` | `hasRole('USER')` |
| POST | `/api/v2/rating/match-results` | `portal record_result` (`app-core.js:8709`) | `SubmitMatchResultRequestDTO` | `SubmittedMatchResultResponseDTO` | 201 | `RecordMatchResultUseCase` | `@gameAuthz.isRankedParticipant`; три формы → одна с `outcome`; идемпотентно по (`roomId`,`gameNumber`) |

### 5.10 `/api/v2/media` (9)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v2/media/memes` | **`db`** `POST /api/db/query` по `memeLibrary` + подписка (`app-core.js:5969`) | `MemeCatalogQueryDTO` | `MemeCatalogPageResponseDTO` | 200 | `ListMemesUseCase` | `hasRole('USER')` |
| GET | `/api/v2/media/memes/{memeId}` | **`db`** `getDoc(doc(db,"memeLibrary",id))` (`app-core.js:5184`) | — | `MemeCardResponseDTO` | 200 | `GetMemeUseCase` | `hasRole('USER')` |
| POST | `/api/v2/media/memes` | **`db`** `setDoc memeLibrary/{id}` (`app-core.js:6424`) | `PublishMemeRequestDTO` | `PublishedMemeResponseDTO` | 201 | `PublishMemeUseCase` | `hasRole('USER')`; автора ставит сервер (A1) |
| DELETE | `/api/v2/media/memes/{memeId}` | **`db`** `deleteDoc memeLibrary/{id}` | — | — | 204 | `WithdrawMyMemeUseCase` | `hasRole('USER')`; только автор — админское снятие живёт в `/api/v2/admin/memes` |
| POST | `/api/v2/media/tickets/video-uploads` | `media upload_ticket` с `kind=video` (`app-core.js:6368`) | `RequestVideoUploadTicketRequestDTO` | `VideoUploadTicketResponseDTO` | 201 | `IssueVideoUploadTicketUseCase` | `hasRole('USER')`; путь строит сервер, `contentLength` подписывается (E9) |
| POST | `/api/v2/media/tickets/poster-uploads` | `media upload_ticket` с `kind=poster` (`MediaServiceImpl:115-123`: свой путь, свой список MIME, свой лимит, свой код ошибки) | `RequestPosterUploadTicketRequestDTO` | `PosterUploadTicketResponseDTO` | 201 | `IssuePosterUploadTicketUseCase` | `hasRole('USER')` |
| POST | `/api/v2/media/tickets/playbacks` | `media playback_ticket` (`app-core.js:5167`) | `RequestPlaybackTicketRequestDTO` | `PlaybackTicketResponseDTO` | 200 | `IssuePlaybackTicketUseCase` | `hasRole('USER')`; метод наконец принимает вызывающего (F15) |
| DELETE | `/api/v2/media/orphan-files` | `media delete_own_orphan` (`app-core.js:6392`) | `DeleteOrphanFileRequestDTO` | — | 204 | `DeleteOrphanFileUseCase` | `hasRole('USER')`; владелец по префиксу пути |
| GET | `/api/v2/media/files/{*path}` | `GET /api/media?path=` | — | — (302, `Location`, `Cache-Control: max-age=300`) | 302 | `ResolveMemeFileUseCase` | permitAll; обязательный префикс `memes/` выражен структурой пути |

### 5.11 `/api/v2/recording` (8)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v2/recording/rooms/{roomId}/games/{gameNumber}` | `recordings start` (`app-core.js:8695`, `:12904`) | `StartRecordingRequestDTO` | `RecordingSessionResponseDTO` | 202 | `StartRecordingUseCase` | `@roomAuthz.isMember`; три формы → одна с полем `state` |
| POST | `/api/v2/recording/rooms/{roomId}/games/{gameNumber}/finish` | `recordings finish` (`app-core.js:8718`, `:12930`) | — | `FinishedRecordingResponseDTO` | 200 | `FinishRecordingUseCase` | `@roomAuthz.isMember`; идемпотентно |
| GET | `/api/v2/recording/rooms/{roomId}/games/{gameNumber}` | `recordings status` (`app-core.js:11552`) | — | `RecordingStatusResponseDTO` | 200 | `GetRecordingStatusUseCase` | `@roomAuthz.isMember` |
| GET | `/api/v2/recording/rooms/{roomId}/games/{gameNumber}/recorder-readiness` | `recordings ready_status` (`app-core.js:12915`) | — | `RecorderReadinessResponseDTO` | 200 | `GetRecorderReadinessUseCase` | `@roomAuthz.isMember` |
| GET | `/api/v2/recording/mine` | `recordings list_mine` (`portal.js:55`) | `MyRecordingQueryDTO` | `MyRecordingsPageResponseDTO` | 200 | `ListMyRecordingsUseCase` | `hasRole('USER')`; `refresh` по 100 записям уезжает в фоновую задачу (B5) |
| PUT | `/api/v2/recording/mine/{recordingId}` | `recordings save` (`app-core.js:14731`) | — | `SavedRecordingResponseDTO` | 200 | `SaveRecordingUseCase` | `hasRole('USER')`; участник записи — инвариант use-case |
| DELETE | `/api/v2/recording/mine/{recordingId}` | `recordings remove_saved` (`portal.js:68`) — сегодня без единой проверки прав | — | — | 204 | `ForgetRecordingUseCase` | `hasRole('USER')` |
| GET | `/api/v2/recording/{recordingId}/playback-urls` | `recordings urls` | — | `RecordingPlaybackUrlsResponseDTO` | 200 | `IssuePlaybackUrlsUseCase` | `@recordingAuthz.canWatch` (сохранена вызывающим либо расшарена ему) |

### 5.12 `/api/v2/admin` (23)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v2/admin/dashboard/live-rooms` | `GET /api/admin?scope=rooms` (`admin.js:302`) | `LiveRoomQueryDTO` | `LiveRoomDashboardResponseDTO` | 200 | `GetLiveRoomDashboardUseCase` | `hasRole('ADMIN')` |
| GET | `/api/v2/admin/dashboard/usage` | `GET /api/admin?scope=stats` (`admin.js:89`) | `UsageStatisticsQueryDTO` | `UsageStatisticsResponseDTO` | 200 | `GetUsageStatisticsUseCase` | `hasRole('ADMIN')`; разбор диапазона дат уехал в `DateRange` (F13) |
| GET | `/api/v2/admin/dashboard/host-metrics` | `HostMetricsService` внутри снимка монитора | — | `HostMetricsResponseDTO` | 200 | `GetHostMetricsUseCase` | `hasRole('ADMIN')` |
| GET | `/api/v2/admin/dashboard/configuration` | блок `env` из `GET /api/health` (10 ключей, сегодня анонимно — A13) | — | `ConfigurationReadinessResponseDTO` | 200 | `CheckConfigurationReadinessUseCase` | `hasRole('ADMIN')` |
| GET | `/api/v2/admin/dashboard/object-storage` | `media config_status` | — | `ObjectStorageConfigResponseDTO` | 200 | `GetObjectStorageConfigUseCase` | `hasRole('ADMIN')` |
| GET | `/api/v2/admin/usage-snapshots` | `GET /api/monitor` (`admin.js:240`) — маршрут был permitAll с проверкой внутри метода (A12) | `UsageSnapshotQueryDTO` | `UsageSnapshotPageResponseDTO` | 200 | `ListUsageSnapshotsUseCase` | `hasRole('ADMIN')` |
| POST | `/api/v2/admin/usage-snapshots` | `POST /api/monitor` от администратора (`admin.js:237`) | — | `AdminUsageSnapshotResponseDTO` | 201 | `TakeUsageSnapshotByAdminUseCase` | `hasRole('ADMIN')` |
| POST | `/api/v2/admin/bans` | `admin ban_user` (`admin.js:359`) | `BanUserRequestDTO` | `BannedUserResponseDTO` | 201 | `BanUserUseCase` | `hasRole('ADMIN')`; поднимает `tokenVersion` через `IdentityCommandPort` |
| DELETE | `/api/v2/admin/bans/{uid}` | `admin unban_user` (сегодня без следа авторства) | — | — | 204 | `LiftBanUseCase` | `hasRole('ADMIN')` |
| POST | `/api/v2/admin/rooms/{roomId}/closure` | `admin close_room` (`admin.js:353`) | `CloseRoomByAdminRequestDTO` | `AdminRoomClosureResponseDTO` | 200 | `CloseRoomByAdminUseCase` | `hasRole('ADMIN')`; закрытие через `RoomCommandPort` |
| GET | `/api/v2/admin/recordings` | `recordings admin_list` (`admin.js:75`) — ветка ADMIN внутри `case` | `AdminRecordingQueryDTO` | `AdminRecordingsPageResponseDTO` | 200 | `ListRecordingsForAdminUseCase` | `hasRole('ADMIN')` |
| GET | `/api/v2/admin/recordings/{recordingId}/playback-urls` | `recordings admin_urls` (`admin.js:77`) | — | `AdminRecordingUrlsResponseDTO` | 200 | `IssueAdminPlaybackUrlsUseCase` | `hasRole('ADMIN')` |
| DELETE | `/api/v2/admin/recordings/{recordingId}` | `recordings admin_delete` (`admin.js:367`) | — | — | 204 | `DeleteRecordingByAdminUseCase` | `hasRole('ADMIN')` |
| PUT | `/api/v2/admin/memes/{memeId}/optimized-variant` | `POST /api/optimize-meme` (`app-core.js:5739`) | `SaveOptimizedMemeRequestDTO` | `OptimizedMemeResponseDTO` | 200 | `SaveOptimizedMemeUseCase` | `hasRole('ADMIN')` |
| POST | `/api/v2/admin/memes/library-reconciliations` | `POST /api/meme-library-sync` (`meme-s3-only.js:122`) — сегодня доступен любому вошедшему | — | `MemeLibraryReconciliationResponseDTO` | 200 | `ReconcileMemeLibraryUseCase` | `hasRole('ADMIN')` |
| DELETE | `/api/v2/admin/memes/{memeId}` | `admin delete_meme_alert` (`app-core.js:5902`) | — | — | 204 | `WithdrawMemeByAdminUseCase` | `hasRole('ADMIN')` |
| GET | `/api/v2/admin/users` | `portal list_users` (`friends.js:15`) — сегодня e-mail до 5000 профилей на маршруте `authenticated` (A6) | `AdminUserQueryDTO` | `AdminUserPageResponseDTO` | 200 | `ListRegisteredUsersUseCase` | `hasRole('OWNER')`; страница вместо всего списка |
| POST | `/api/v2/admin/test-rooms/{roomId}/bots` | `test-bots setup` (`app-core.js:12123`) | `SetUpTestBotsRequestDTO` | `TestBotSquadResponseDTO` | 201 | `SetUpTestBotsUseCase` | `hasRole('ADMIN')`; фича `bot_enabled` и владение тестовой комнатой — предусловия use-case |
| DELETE | `/api/v2/admin/test-rooms/{roomId}/bots` | `test-bots stop` (`app-core.js:12418`) | — | — | 204 | `StopTestBotsUseCase` | `hasRole('ADMIN')` |
| POST | `/api/v2/admin/test-rooms/{roomId}/bots/turn` | `test-bots tick` (`test-mode.js:351`) | — | `TestBotTurnResponseDTO` | 200 | `AdvanceTestBotTurnUseCase` | `hasRole('ADMIN')`; 11 условных ключей → одна схема с nullable-полями |
| POST | `/api/v2/admin/test-rooms/{roomId}/bots/video-tokens` | `POST /api/token` с чужим `participant_identity` (`test-mode.js:286`) | `IssueTestBotVideoTokenRequestDTO` | `TestBotVideoTokenResponseDTO` | 201 | `IssueTestBotVideoTokenUseCase` | `hasRole('ADMIN')` |
| POST | `/api/v2/admin/test-rooms/{roomId}/bots/owner-farts` | `test-bots fart` (`app-core.js:2281`) | `FireOwnerFartRequestDTO` | `FiredOwnerFartResponseDTO` | 201 | `FireOwnerFartUseCase` | `hasRole('ADMIN')`; идемпотентно по `event_id` |
| POST | `/api/v2/admin/test-rooms/{roomId}/bots/speech-syntheses` | `POST /api/tts` (`app-core.js:2041`, озвучка «облака мыслей»; достижимо только в тестовой комнате — `:1605-1611`) | `SynthesizeSpeechRequestDTO` | `SynthesizedSpeechResponseDTO` | 200 | `SynthesizeSpeechUseCase` | `hasRole('ADMIN')`; `voice_id` принимается через `@JsonAlias` |

### 5.13 `/api/v2/machine` (12)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v2/machine/recorder/sessions` | `GET /api/recording-state?bootstrap=1` (`app-core.js:13906`), выдающий безличный `recorderToken` | `OpenRecorderSessionRequestDTO` | `RecorderSessionResponseDTO` | 201 | `OpenRecorderSessionUseCase` | `RECORDER_BOOTSTRAP` (подпись в заголовке) |
| GET | `…/machine/recorder/rooms/{roomId}/games/{gameNumber}/scene` | `GET /api/recording-state` без флагов (`recording-view.js:37`, опрос 4 раза в секунду) | — | `RecorderSceneResponseDTO` | 200 | `ReadRecorderSceneUseCase` | `hasRole('RECORDER')`; `avatarDataUrl` заменён ссылкой (D4) |
| GET | `…/machine/recorder/rooms/{roomId}/games/{gameNumber}/room-state` | `GET /api/recording-state?state=1` (`app-core.js:14013`) | — | `RecorderRoomStateResponseDTO` | 200 | `ReadRecorderRoomStateUseCase` | `hasRole('RECORDER')` |
| GET | `…/machine/recorder/rooms/{roomId}/games/{gameNumber}/current-word` | `GET /api/recording-state?word=1` (`app-core.js:1580`) | — | `RecorderWordResponseDTO` | 200 | `ReadRecorderWordUseCase` | `hasRole('RECORDER')` |
| POST | `…/machine/recorder/rooms/{roomId}/games/{gameNumber}/ready-signal` | мутирующий `GET /api/recording-state?ready=1` (`app-core.js:13995`) | `SignalRecorderReadyRequestDTO` | `RecorderReadySignalResponseDTO` | 200 | `SignalRecorderReadyUseCase` | `hasRole('RECORDER')` |
| POST | `…/machine/recorder/rooms/{roomId}/games/{gameNumber}/start-signal` | мутирующий `GET /api/recording-state?started=1` (`app-core.js:14051`) | `SignalRecorderStartedRequestDTO` | `RecorderStartSignalResponseDTO` | 200 | `SignalRecorderStartedUseCase` | `hasRole('RECORDER')` |
| POST | `…/machine/recorder/rooms/{roomId}/games/{gameNumber}/ceremony-completion` | мутирующий `GET /api/recording-state?ceremony_done=1` (останавливает Egress) | — | `RecorderCeremonyResponseDTO` | 200 | `CompleteRecorderCeremonyUseCase` | `hasRole('RECORDER')` |
| POST | `/api/v2/machine/webhooks/livekit-egress` | `POST /api/recording-egress?sig=` (`signing_key` LiveKit сегодня не проверяется) | `LiveKitEgressWebhookRequestDTO` | `EgressWebhookAckResponseDTO` | 200 | `ApplyEgressWebhookUseCase` | `hasRole('EGRESS')` |
| POST | `/api/v2/machine/maintenance/room-sweeps` | `GET` и `POST /api/cleanup-rooms?sweep=1` (ранний выход через `NoRoomRequested` убран — E8) | — | `RoomSweepResponseDTO` | 200 | `SweepAbandonedRoomsUseCase` | `hasRole('CRON')` |
| POST | `/api/v2/machine/maintenance/recording-sweeps` | `GET` и `POST /api/cleanup-recordings` | — | `RecordingSweepResponseDTO` | 200 | `SweepExpiredRecordingsUseCase` | `hasRole('CRON')` |
| POST | `/api/v2/machine/maintenance/token-sweeps` | новое: `SaverUser.deleteExpiredTokens` реализован (`UserManager:164-172`), но не вызывается ни разу (A11) | — | `TokenSweepResponseDTO` | 200 | `SweepExpiredTokensUseCase` | `hasRole('CRON')` |
| POST | `/api/v2/machine/usage-snapshots` | `POST /api/monitor` с `X-Hot-Hat-Monitor-Secret` — вторая половина адреса, менявшего форму ответа по способу входа | `IngestUsageCounterRequestDTO` | `MachineUsageSnapshotResponseDTO` | 201 | `TakeUsageSnapshotByAgentUseCase` | `hasRole('MONITOR_AGENT')` |

### 5.14 `/api/v2/app` (3)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v2/app/health` | `GET /api/health` без блока `env` | — | `HealthResponseDTO` | 200 | `CheckHealthUseCase` | permitAll |
| GET | `/api/v2/app/features/{name}` | `GET /api/features/{name}` (`features.js:54`) | — | `FeatureFlagResponseDTO` | 200 | `ReadFeatureFlagUseCase` | permitAll; имя из закрытого набора, кеш ограниченного размера (G3) |
| POST | `/api/v2/app/analytics-events` | `POST /api/analytics` (`app-core.js:910`) | `RecordAnalyticsEventRequestDTO` | `AnalyticsEventAcceptedResponseDTO` | 202 | `RecordAnalyticsEventUseCase` | `hasRole('USER')`; `eventType` — enum из шести значений; 202 вместо 201-на-дубликате (D9) |

### 5.14a `/api/v2/conference` (14) — видео-чат

Область добавлена после плана: созвон до шестнадцати друзей без запуска игры.
Возможность придумана в ветке `dev` фронтенда (SCRUM-18) поверх Vercel-функций
и Firestore — там одно тело `POST /api/token` с полем `conference_action`
выбирало между тринадцатью действиями, а страница опрашивала состав раз в
восемь секунд и ленту раз в пять. Здесь у каждого действия свой адрес и свой
уровень прав, а чтение живёт кадром `/ws/v2/conference/{id}` (см. 5.15).
Пакет `ru.hothat.conference`, таблицы `v2.video_conference`,
`v2.video_conference_member`, `v2.video_conference_message` (V22).

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v2/conference` | `conference_action:"create"` | — | `ConferenceResponseDTO` | 201 | `CreateConferenceUseCase` | `hasRole('USER')` |
| GET | `/api/v2/conference/{conferenceId}` | `conference_action:"get"` | — | `ConferenceResponseDTO` | 200 | `GetConferenceUseCase` | участник (`CONFERENCE_INVITE_REQUIRED`) |
| POST | `/api/v2/conference/{conferenceId}/video-token` | `conference_action:"join"` | — | `ConferenceVideoTokenResponseDTO` | 201 | `IssueConferenceVideoTokenUseCase` | участник; имя — из карточки, не из тела |
| DELETE | `/api/v2/conference/{conferenceId}/members/me` | `conference_action:"leave"` | — | — | 204 | `LeaveConferenceUseCase` | участник; хозяин не выходит |
| DELETE | `/api/v2/conference/{conferenceId}/members/{uid}` | `conference_action:"kick"` | — | `ConferenceResponseDTO` | 200 | `RemoveConferenceMemberUseCase` | хозяин; себя нельзя (`CONFERENCE_HOST_PROTECTED`) |
| POST | `/api/v2/conference/{conferenceId}/game-room` | `conference_action:"create_game_room"` | `CreateConferenceGameRoomRequestDTO` | `ConferenceResponseDTO` | 201 | `CreateConferenceGameRoomUseCase` | хозяин; комнату заводит `room.spi.RoomLineupPort` одной транзакцией |
| GET | `/api/v2/conference/invites` | `conference_action:"pending_invites"` (опрос раз в 4 с) | — | `ConferenceInvitesResponseDTO` | 200 | `ListMyConferenceInvitesUseCase` | свои; тот же список — в кадре `/ws/v2/me/social` |
| POST | `/api/v2/conference/{conferenceId}/invites` | `conference_action:"invite"` + `"departed"` | `InviteToConferenceRequestDTO` | `SentConferenceInviteResponseDTO` | 201 | `InviteToConferenceUseCase` | участник, только друга; ушедшего без выхода зовут снова после проверки у видеоузла |
| POST | `/api/v2/conference/{conferenceId}/invites/me/acceptance` | `conference_action:"accept"` | — | `AnsweredConferenceInviteResponseDTO` | 200 | `AnswerConferenceInviteUseCase` | адресат |
| POST | `/api/v2/conference/{conferenceId}/invites/me/rejection` | `conference_action:"decline"` | — | `AnsweredConferenceInviteResponseDTO` | 200 | `AnswerConferenceInviteUseCase` | адресат |
| GET | `/api/v2/conference/{conferenceId}/messages` | `conference_action:"chat_list"` | — | `ConferenceMessagesResponseDTO` | 200 | `ReadConferenceMessagesUseCase` | участник |
| POST | `/api/v2/conference/{conferenceId}/messages` | `conference_action:"chat_send"` (текст) | `PostConferenceMessageRequestDTO` | `SentConferenceMessageResponseDTO` | 201 | `PostConferenceMessageUseCase` | участник |
| POST | `/api/v2/conference/{conferenceId}/file-messages` | `conference_action:"chat_send"` (attachment) | `PostConferenceFileMessageRequestDTO` | `SentConferenceMessageResponseDTO` | 201 | `PostConferenceFileMessageUseCase` | участник; ключ обязан лежать в своей папке (`CONFERENCE_FILE_INVALID`) |
| POST | `/api/v2/conference/{conferenceId}/files/upload-tickets` | `POST /api/conference-media` `upload_ticket` | `RequestConferenceUploadTicketRequestDTO` | `ConferenceUploadTicketResponseDTO` | 201 | `IssueConferenceUploadTicketUseCase` | участник; до 25 МБ |

Открытого адреса файлов (`GET /api/conference-media?path=`) больше нет:
вложения видят только участники, ссылка подписывается на два часа при каждом
чтении ленты и приезжает в `ConferenceFileView.url`.

### 5.15 `/ws/v2` — восемь каналов и три кадра

| Событие | Путь | Заменяет | Hello DTO | Event DTO | Use-case | Права |
|---|---|---|---|---|---|---|
| CONNECT | `/ws/v2/lobby` | **`db`** подписка `query(rooms, where phase in […])` (`home/home.js:93`) | `LobbyChannelHelloDTO` | `LobbyChannelEventDTO` | `StreamLobbyChannelUseCase` | `hasAnyRole('GUEST','USER')` |
| CONNECT | `/ws/v2/room/{roomId}` | **`db`** шесть подписок игрового экрана (`app-core.js:8634`, `:8750`, `:8761`, `:8788`, `:8795`, `:8806`) | `RoomChannelHelloDTO` | `RoomChannelEventDTO` | `StreamRoomChannelUseCase` | `@roomAuthz.isMemberOrSpectator` |
| CONNECT | `/ws/v2/me/social` | **`db`** подписки `realtime-social.js:108`, `:110`, `:111`, `:113`; hello-кадр несёт значки шапки портала | `SocialChannelHelloDTO` | `SocialChannelEventDTO` | `StreamSocialChannelUseCase` | `hasRole('USER')` |
| CONNECT | `/ws/v2/chat/{peerUid}` | **`db`** подписка `directChats/{pair}/messages` (`realtime-social.js:99`, `portal.js:121`, `friends.js:20`) | `DirectChatHelloDTO` | `DirectChatEventDTO` | `StreamDirectChatUseCase` | `@chatAuthz.areFriends` |
| CONNECT | `/ws/v2/team/{teamId}/preflight` | **`db`** подписка `rankedTeamPreflights/{teamId}` (`portal.js:125`) | `PreflightHelloDTO` | `PreflightEventDTO` | `StreamPreflightUseCase` | `@teamAuthz.isMember` |
| CONNECT | `/ws/v2/media/memes` | **`db`** подписка `collection(memeLibrary)` (`app-core.js:5969`) | `MemeLibraryHelloDTO` | `MemeLibraryEventDTO` | `StreamMemeLibraryUseCase` | `hasRole('USER')` |
| CONNECT | `/ws/v2/machine/recorder/rooms/{roomId}` | **`db`** `startRecorderDbMirror` — подписки `rooms/{id}` и `rooms/{id}/teams` (`app-core.js:13843`, `:13870`) | `RecorderChannelHelloDTO` | `RecorderChannelEventDTO` | `StreamRecorderChannelUseCase` | `hasRole('RECORDER')` |
| CONNECT | `/ws/v2/conference/{conferenceId}` | опросы `chat_list` (5 с) и `get` (8 с) страницы видео-чата ветки dev; заведённая комната — вместо data-пакета LiveKit `game_room_created` | `ConferenceChannelHelloDTO` | `ConferenceChannelEventDTO` | `StreamConferenceChannelUseCase` | участник; выгнанному — отказ `CONFERENCE_INVITE_REQUIRED` и закрытие |
| FRAME | `/ws/v2/**` `ping` | `{type:"ping"}` → `{type:"pong"}` (`DocumentSocketHandler`) | `ChannelPingFrameDTO` | `ChannelPongFrameDTO` | `HandleChannelHeartbeatUseCase` | открытая сессия |
| FRAME | `/ws/v2/lobby` `spotlight` | **`db`** три подписки состава выбранной комнаты (`home/home.js:88`) + `rooms/{id}/players/{uid}` (`live-preview.js:34`) | `SpotlightFrameDTO` | `RoomPreviewEventDTO` | `SpotlightLobbyRoomUseCase` | тот же уровень, что у канала |
| FRAME | `/ws/v2/**` `unsubscribe` | `{type:"unsubscribe", id}` (`db.js:389-398`) | `ChannelUnsubscribeFrameDTO` | — | `DropChannelSubscriptionUseCase` | открытая сессия |

---

## 6. Схема базы

92 таблицы вместо сегодняшних 39, ни одной колонки с двумя писателями, два `jsonb` на всю базу
(`moderation_action.details` — набор полей зависит от вида действия; `recording_egress_event.payload` —
тело вебхука задаёт внешняя система). Единый ключ игрока — `player_id uuid`, единый ключ комнаты —
`room.id text` вида `hat-[0-9a-f]{16}` (он ходит по чатам как ссылка-приглашение и остаётся кодом,
а не суррогатом).

### 6.1 Четыре расхождения между кластерами, разрешённые здесь

| Расхождение | Решение |
|---|---|
| `uid varchar(160)` / `varchar(32)` против `player_id uuid` | Везде `player_id uuid`. Фактические значения сегодня — 28-символьный base64url, длина 160 не значила ничего. |
| `match_state` против `match` | Таблица называется `match`; `match_state` как имя не используется нигде. |
| `ranked_team.id varchar(24)` против `uuid` | Суррогаты (`ranked_team`, `team_invite`, `room_team`, `room_invite`, `match*`, `meme`, `recording`) — `uuid`. Кодами остаются только `room.id` и `player_profile.nickname`. |
| `room.recording_enabled*` против `room_recording_policy` | Колонки из `room` убраны, таблица `room_recording_policy` оставлена: флаг ставит владелец сервиса ради записи, писатель — домен `recording`. |

### 6.2 Таблицы

| Таблица | Владелец | Назначение | Ключевые колонки | Индексы |
|---|---|---|---|---|
| `user_account` | auth | Чем игрок доказывает, что он это он | `player_id` PK, `kind` (guest либо member), `email` UNIQUE, `password_hash`, `token_version`, `member_since` | `ux_email`; `(member_since DESC) WHERE kind='member'`; `(created_at) WHERE kind='guest'` |
| `account_role` | auth | Кто администратор и кто владелец | `(player_id, role)` PK, `granted_by` | `UNIQUE(role) WHERE role='OWNER'` |
| `refresh_token` | auth | Долгоживущая половина пары, она же список сессий | `token_hash` PK bytea, `family_id`, `replaced_by`, `revoked_reason`, `created_ip` | `(family_id) WHERE revoked_at IS NULL`; `(expires_at)` |
| `password_reset_token` | auth | Одноразовая ссылка восстановления | `token_hash` PK, `used_at`, `invalidated_at`, `requested_ip` | `(player_id, created_at DESC)`; `(expires_at) WHERE не использован` |
| `player_profile` | profile | Карточка: имя и дивизион | `player_id` PK, `nickname`, `nickname_key` GENERATED, `division_language`, `ui_language`, `division_locked_at`, `version` | `ux_nickname_key` |
| `player_avatar` | profile | Аватар до 90 КБ отдельной строкой | `player_id` PK, `media_type`, `bytes`, `sha256` | только PK (ETag по `sha256`) |
| `player_presence` | profile | Где игрок сейчас, когда его видели | `player_id` PK, `last_seen_at`, `active_room_id` | `(last_seen_at DESC)`, fillfactor 70 |
| `legal_consent` | profile | Журнал принятых документов | `(player_id, document, document_version)` UNIQUE, `source_ip` | `(player_id, document, accepted_at DESC)`; `(document, document_version)` |
| `nickname_change_request` | profile | Заявка на занятый ник | `id` PK, `status`, `decided_by` | `UNIQUE(player_id) WHERE status='new'`; `(created_at) WHERE status='new'` |
| `user_ban` | admin | История банов, а не флаг | `id` PK, `player_id`, `banned_by`, `lifted_at`, `lifted_by` | `ux_active UNIQUE(player_id) WHERE lifted_at IS NULL`; `(banned_at DESC)` |
| `moderation_action` | admin | След действий модератора | `id` PK, `actor_player_id`, `action`, `subject_type`, `subject_id`, `details jsonb` | `(created_at DESC)`; `(subject_type, subject_id, created_at DESC)` |
| `friendship` | friends | Подтверждённая дружба, одна строка на пару | `(player_low, player_high)` PK, CHECK `low<high` | PK; `(player_high) INCLUDE (created_at)` |
| `friend_request` | friends | Заявка со своим id в API | `id` PK, `requester`, `addressee`, `status`, `answered_at` | `UNIQUE(low,high) WHERE PENDING`; inbox/outbox частичные |
| `direct_chat` | chat | Шапка переписки | `chat_id` PK, `UNIQUE(low,high)`, `last_message_id`, `last_message_at` | `ux_pair` |
| `direct_chat_member` | chat | Непрочитанное и докуда дочитано | `(chat_id, player_id)` PK, `unread_count`, `last_read_message_id` | `(player_id, chat_id)`; `(player_id) WHERE unread_count>0` |
| `direct_chat_message` | chat | Одно сообщение переписки | `id` PK (он же курсор), `kind`, `body` ≤800, `shared_recording_id`, `room_invite_id` | `(chat_id, id DESC)`; `(room_invite_id) WHERE NOT NULL` |
| `direct_chat_photo` | chat | Картинка сообщения до 120 КБ | `message_id` PK, `data_url`, `width`, `height` | только PK |
| `ranked_team` | team | Постоянная пара | `id` PK, `name`, `name_key` GENERATED, `status`, `version` | `ux_name_key` |
| `ranked_team_logo` | team | Логотип до 280 КБ | `team_id` PK, `data_url` | только PK |
| `ranked_team_member` | team | Состав команды | `(team_id, player_id)` PK, `role`, `status` | `UNIQUE(player_id) WHERE ACTIVE`; `UNIQUE(team_id) WHERE CAPTAIN AND ACTIVE` |
| `team_invite` | team | Приглашение напарника | `id` PK, `status`, `expires_at` | inbox частичный; `(expires_at) WHERE PENDING` |
| `team_preflight_session` | team | Сессия предматчевой проверки | `team_id` PK, `session_seq`, `intent`, `expires_at`, `version` | PK; `(expires_at) WHERE открыта` |
| `team_preflight_participant` | team | Готовность одного напарника | `(team_id, player_id)` PK, `session_seq`, `media_ok`, `ready`, CHECK `NOT ready OR media_ok` | PK |
| `ranking_board` | rating | Таблица сезона и её чемпион | `board_id` PK, `UNIQUE(year, season, mode, division)`, `champion_*` | `ux_board` |
| `season_team_standing` | rating | Положение команды в сезоне | `(board_id, team_id)` PK, `points`, `games`, `wins`, `technical_forfeits` | `(board_id, points DESC, team_id)`; `(team_id, board_id)` |
| `season_player_standing` | rating | Личное положение игрока | `(board_id, player_id)` PK, `points`, `synced_team_points` | `(board_id, points DESC, player_id)`; `(player_id, board_id)` |
| `match_result_event` | rating | Факт зачёта партии | `(room_id, game_number)` PK, `outcome`, `technical`, `recorded_by` | PK — барьер идемпотентности; `(board_id, recorded_at DESC)` |
| `match_result_team` | rating | Разбор зачёта по командам | `(room_id, game_number, team_id)` PK, `place`, `points_delta`, `culprit` | PK; `(team_id, room_id)` |
| `room` | room | Паспорт комнаты | `id` PK text, `kind`, `name`, `capacity`, `turn_duration_ms`, `game_mode`, `created_by` НЕИЗМЕНЯЕМ, `version` | `(game_language) WHERE публичная`; `(ranked_team_id)` |
| `room_lifecycle` | room | Крупная фаза, счётчик партий | `room_id` PK, `state`, `game_number`, `last_activity_at`, `closed_reason`, `version` | `(state) WHERE открыта`; `(last_activity_at) WHERE открыта` |
| `room_host` | room | Кто хозяин сейчас | `room_id` PK, `host_player_id`, `reclaim_player_id`, `last_activity_kind`, `version` | `(host_player_id)` |
| `room_host_transfer` | room | История передач хозяйства | `id` PK, `from`, `to`, `reason` | `(room_id, occurred_at DESC)` |
| `room_player` | room | Место игрока: принадлежность | `(room_id, player_id)` PK, `team_id`, `bot`, `invited_by`, `version` | `(player_id)`; `(room_id, team_id)` |
| `room_spectator` | room | Место зрителя | `(room_id, player_id)` PK, `version` | `(player_id)`; `(room_id)` |
| `room_presence` | room | Сердцебиение и состояние устройств | `(room_id, player_id)` PK, `last_seen_at`, `camera_on`, `mic_on`, `media_revision` | `(room_id, last_seen_at DESC)` |
| `room_presence_counter` | room | Счётчик живых игроков для витрины | `room_id` PK, `active_players`, `measured_at` | `(measured_at DESC) WHERE active_players>0` |
| `room_team` | room | Команда как место в составе | `id` PK, `(room_id, slot)` UNIQUE, `ranked_team_id`, `version` | `(room_id, slot)` |
| `room_chat_message` | room | Сообщение чата комнаты | `id` PK, `author_player_id`, `author_name` (снимок), `author_seat`, `body`, `image_data_url`, `version` | `(room_id, created_at DESC)` |
| `room_invite` | room | Приглашение друга в комнату | `id` PK, `state`, `game_number_at_invite`, `chat_message_id`, `expires_at`, `version` | `(to_player, state, created_at DESC)`; `(expires_at) WHERE pending` |
| `matchmaking_room` | lobby | Комната, которую подбор собирает | `room_id` PK, `ranked`, `target_players`, `rating_target`, `deadline_at`, `version` | `(ranked, mode, language, target, rating) WHERE открыта`; `(deadline_at)` |
| `matchmaking_ticket` | lobby | Заявка игрока либо пары | `id` PK, `party_size`, `ranked_team_id`, `team_slot`, `state`, `version` | `(room_id, state)`; `(requested_by, state)` |
| `matchmaking_ticket_member` | lobby | Кто стоит в очереди по заявке | `(ticket_id, player_id)` PK, `UNIQUE(player_id)` глобально | покрыт уникальностью |
| `word_submission` | game | Слово, сданное в шляпу до старта | `id` PK, `UNIQUE(room_id, author, normalized)`, `normalized` GENERATED | `(room_id)`; `(room_id, author)` |
| `match` | game | Партия как факт | `id` PK, `UNIQUE(room_id, game_number)`, замороженные условия, `state`, `finish_reason`, `version` | `UNIQUE(room_id) WHERE running`; `(ranked, finished_at DESC) WHERE finished` |
| `match_team` | game | Команда партии и её место в очереди | `(match_id, room_team_id)` PK, `UNIQUE(match_id, turn_order)` | `(match_id, turn_order)` |
| `match_player` | game | Замороженный ростер | `(match_id, player_id)` PK, `display_name`, `seat_in_team` | `(match_id, room_team_id)`; `(player_id, match_id)` |
| `match_turn` | game | Ход как сущность | `id` PK (это и есть `turnId`), `turn_no`, `state`, `current_word_id`, `deadline_at`, `version` | `UNIQUE(match_id) WHERE state<>'closed'`; `(match_id, turn_no DESC)` |
| `match_word` | game | Шляпа: слово и позиция в колоде | `id` PK, `UNIQUE(match_id, bag_position)`, `state` | `(match_id, bag_position) WHERE in_bag` |
| `match_turn_word` | game | Исход слова в ходе | `id` PK (это `wordId` апелляции), `outcome`, `ordinal`, `invalidated` | `(turn_id, outcome) WHERE NOT invalidated`; `(turn_id, resolved_at DESC) WHERE guessed` |
| `match_appeal` | game | Окно апелляции по ходу | `turn_id` PK, `ends_at`, `closed_at`, `version` | `(match_id) WHERE открыта` |
| `match_appeal_vote` | game | Голос за отмену слова | `(turn_word_id, voter)` PK | PK; `(voter)` |
| `match_pause` | game | Пауза: строка есть — пауза есть | `(match_id, kind)` PK, `turn_remaining_ms`, `appeal_remaining_ms`, `version` | `(started_at) WHERE kind='disconnect'` |
| `match_pause_absentee` | game | Кого не хватает в LiveKit сейчас | `(match_id, player_id)` PK, `missing_since` | PK |
| `sabotage_entitlement` | sabotage | Право играть с диверсиями | `player_id` PK, `unlimited`, `free_games_limit`, `free_games_used` | PK |
| `sabotage_game_grant` | sabotage | Журнал списаний бесплатных партий | `id` PK, `UNIQUE(player_id, room_id, game_number)` | ключ идемпотентности; `(player_id, consumed_at DESC)` |
| `player_default_loadout` | sabotage | Стартовая обойма учётки, пять строк | `(player_id, slot_index)` PK, `UNIQUE(player_id, meme_id)` | PK; `(meme_id)` под FK RESTRICT |
| `match_saboteur` | sabotage | Кулдаун и курсор выдачи мемов | `(match_id, player_id)` PK, `cooldown_until`, `meme_cycle_cursor`, `is_bot` | PK — цель `FOR UPDATE` |
| `match_player_ammo` | sabotage | Боезапас: строка на вид патрона | `(match_id, player_id, ammo_type)` PK, `amount` | PK |
| `match_loadout_slot` | sabotage | Обойма партии: пять слотов и корзина | `(match_id, player_id, slot_index)` PK, `bucket`, `bucket_order` | `(match_id, player_id, bucket, bucket_order)` |
| `match_reward_progress` | sabotage | Прогресс команды к редким диверсиям | `(match_id, team_id)` PK, `guessed_total`, `recipient_cursor` | PK |
| `match_effect_lock` | sabotage | Занятость канала эффекта | `(match_id, channel)` PK, `locked_until`, `turn_id` | PK |
| `sabotage_event` | sabotage | Журнал применённых диверсий | `id` PK, `UNIQUE(match_id, seq)`, `weapon_type`, `attacker`, `target`, `duration_ms` | `(match_id, seq DESC)` — «текущее» и «последние 24» |
| `replacement_clip` | sabotage | Слот подменного клипа | `id` PK, `state`, `record_deadline`, `consumed_event_id` | `(match_id, attacker) WHERE живой`; `(record_deadline) WHERE RECORDING` |
| `meme` | media | Карточка мема | `id` PK, `slug` UNIQUE, `title_search` GENERATED, `origin`, `status`, `version` | `(division, status, created_at DESC)`; GIN по `title_search` |
| `meme_asset` | media | Файл мема в хранилище | `(meme_id, kind)` PK, `storage_key` UNIQUE, `state`, `revision` | `ux_storage_key`; `(upload_expires_at) WHERE PENDING` |
| `recording` | recording | Паспорт записи партии | `id` PK, `UNIQUE(room_id, game_number)`, `word_count`, `started_by` | `(created_at DESC)`; `(division, created_at DESC)` |
| `recording_participant` | recording | Кто участвовал в записанной партии | `(recording_id, player_id)` PK, `nickname` (снимок) | `(player_id)` — «я участник» |
| `recording_team` | recording | Составы и счёт команд записи | `(recording_id, team_id)` PK, `score`, `is_winner` | только PK |
| `recording_egress_job` | recording | Жизненный цикл задания Egress | `recording_id` PK, `egress_id` UNIQUE, `state`, `last_sync_at`, `version` | `ux_egress_id`; `(state, start_requested_at) WHERE незавершено` |
| `recording_egress_event` | recording | Журнал вебхуков LiveKit | `id` PK, `delivery_id` UNIQUE, `applied`, `payload jsonb` | `ux_delivery_id`; `(recording_id, received_at DESC)` |
| `recorder_session` | recording | Отметки страницы-рекордера | `recording_id` PK, `ready_at`, `start_signal_at`, `ceremony_completed_at`, `version` | `(ready_at) WHERE не начал снимать` |
| `recording_artifact` | recording | Файл записи в хранилище | `recording_id` PK, `object_path` UNIQUE, `size_bytes`, `deleted_at` | `ux_object_path`; `(recording_id) WHERE не удалён` |
| `recording_retention` | recording | Срок хранения и счётчик сохранений | `recording_id` PK, `expires_at`, `save_count`, `version` | `(expires_at) WHERE save_count=0` |
| `recording_save` | recording | Кто положил запись к себе | `(recording_id, player_id)` PK | `(player_id, saved_at DESC)` |
| `recording_share` | recording | Кому запись открыта по ссылке | `(recording_id, grantee)` PK, `shared_by` | `(grantee)` |
| `room_recording_policy` | recording | Включена ли запись в комнате | `room_id` PK, `enabled`, `updated_by` | `(room_id) WHERE enabled` |
| `analytics_event` | app | Продуктовые события клиента | `id` PK, `event_key` UNIQUE, `event_type`, шесть типизированных колонок вместо `payload` | `ux_event_key`; `(occurred_at)`; `(event_type, occurred_at)` |
| `usage_snapshot` | admin | Один снимок расхода | `id` PK, `taken_by` (schedule, admin, agent), `local_date` | `(taken_at DESC)`; `(local_date, taken_at DESC)` |
| `usage_metric_sample` | admin | Одна метрика снимка | `(snapshot_id, metric_key)` PK, `used_value`, `limit_value`, `accuracy` | `(metric_key, snapshot_id DESC)` |
| `service_health_probe` | admin | Проверки живости снимка | `(snapshot_id, probe_key)` PK, `status`, `latency_ms` | `(probe_key, snapshot_id DESC)`; `(snapshot_id) WHERE status<>'ok'` |
| `usage_traffic_meter` | admin | Последнее сырое показание счётчика ОС | `meter_id` PK, `tx_bytes`, `version` | только PK |
| `usage_traffic_period` | admin | Накопленный трафик за период | `(meter_id, period_kind, period_key)` PK, `tx_bytes` | `(period_kind, period_key DESC)` |
| `usage_alert` | admin | Тревога о превышении порога | `id` PK, `UNIQUE(period_kind, period_key, metric_key, threshold)` | `ux_period_metric`; `(created_at DESC)` |
| `usage_report` | admin | Ежедневный отчёт о расходе | `report_date` PK, `snapshot_id`, `email_id` | PK |
| `outbound_email` | admin | Журнал исходящих писем | `id` PK, `kind`, `recipient`, `state` | `(requested_at) WHERE sent` — квота Resend; `(kind, requested_at DESC)` |
| `maintenance_lease` | machine | Аренда на прогон уборки | `job` PK, `leased_until`, `last_run_at`, `version` | только PK |
| `maintenance_run` | machine | История прогонов уборки | `id` PK, `triggered_by`, `state`, `checked_count`, `affected_count` | `(job, started_at DESC)`; `(started_at DESC) WHERE failed` |
| `maintenance_run_target` | machine | Что именно тронул прогон | `(run_id, target_kind, target_id)` PK, `outcome`, `reason` | `(target_kind, target_id, run_id DESC)` |
| `feature_flag` | app | Переключатель возможности | `name` PK, `enabled`, `updated_by` | только PK |
| `test_bot_run` | admin | Прогон тестовых ботов в комнате | `id` PK, `owner`, `state`, `bot_count` | `UNIQUE(room_id) WHERE running`; `(owner, started_at DESC)` |
| `test_bot` | admin | Один бот прогона | `(run_id, bot_player_id)` PK, `slot_index`, `last_shot_at` | `(run_id, last_shot_at NULLS FIRST)` |
| `test_bot_schedule` | admin | Часы прогона ботов | `run_id` PK, `next_action_at`, `next_sabotage_at`, `next_chat_at`, `version` | `(next_action_at)` |

### 6.3 Что выброшено из нынешней схемы и почему

**Дубли ключа.** `friend_link.pair`, `direct_chat.pair`, `direct_chat_message.pair` (VARCHAR(340) —
склейка двух uid, у сообщений ещё и внешним ключом), `ranked_result_event.game_key`,
`sabotage_game_use.id` («roomId-gameNumber-uid»), `season_ranking.id` («2026-winter-sabotage-ru»),
`recording.id` («hat-…-3») — все склеенные строки распались на колонки либо на суррогаты.
Таблица `nickname_index` выброшена целиком: уникальность даёт `UNIQUE(player_profile.nickname_key)`,
а вместе с таблицей исчезают починка индексов (`ProfileServiceImpl:584-640`, 57 строк), фолбэки
`getByNicknameKey`/`getNicknamesOfUid` и находка B9 (merge по присвоенному `@Id` переписывал чужую строку).
Таблица `ranked_team_name` выброшена: `name_key` стал generated-колонкой.

**Все 28 jsonb-полей разобраны.** `room.bag` → строки `match_word`; `turn_guessed_words` →
`match_turn_word`; `appeal_votes` → `match_appeal_vote`; `sabotage_locks` → `match_effect_lock`;
`replacement_recordings` → `replacement_clip`; `arsenal` → `match_player_ammo`; пять массивов обоймы →
`match_loadout_slot`; `team_rosters`/`game_player_names_by_uid` → `match_player`; `technical_termination`
→ колонки `match` и строки `match_pause_absentee`; `test_bot_runtime` → три таблицы тест-ботов, из
одиннадцати ключей выжил один; `direct_chat.unread_counts`/`unread_for`/`last_read_at_ms` →
`direct_chat_member`; `attachment` → `direct_chat_photo` + `shared_recording_id`;
`usage_daily.latest` → `usage_snapshot` + `usage_metric_sample` + `service_health_probe`;
`legal_consent.versions` → строки; `saved_by`/`shared_with` → `recording_save`/`recording_share`.

**Выводимое (денормализованных счётчиков нет — расходиться нечему).** `room.word_count`, `words_left`,
`word_revision`, `current_turn_score`, `room_team.score`, `current_team_index`, `explainer_name`,
`guesser_name`, `managed_matchmaking`, `room_team.member_uids`, `social_inbox` целиком (14 колонок),
`ranked_result_event.culprit_team_ids`, `recording.participant_uids`, `winner_team_ids`,
`winning_score`, `total_score`, `shared_count`, `usage_mail_counter`, `default_meme_loadout_confirmed_at`.

**Мёртвое (пишется, не читается — проверено грепом геттеров).** `app_user.legal_accepted`,
`legal_versions`, `legal_accepted_at`, `adult_confirmed`, `division_backfilled_at`,
`division_explicitly_chosen_at`, `last_login_at`, `password_updated_at`, `active_room_updated_at`,
`default_meme_loadout_updated_at`, `default_meme_loadout_confirmed_at`; `room.video_provider`,
`matchmaking_min_players`, `sabotage_cooldown_until` (везде присваивается 0),
`sabotage_locks.replacementRecordingUntil`, `test_bot_voice_effect_*`; `room_player.clock_probe_at`
(костыль калибровки часов — заменён `GET /api/v2/game/clock`); `ranked_team.rating` (вечная тысяча);
`season_ranking.champion` (не пишется никем); `recording.secret_word_recorded`, `recording_view`,
`start_lock_at_ms`; `usage_daily.vps_network_tx_bytes`; `support_request.type`/`destination`.

**Копии чужих данных.** `room_player.name`, `room_player.avatar_data_url` (до 140 000 символов на
строку места, тиражируются в каждый снимок комнаты и в состояние рекордера, которое опрашивается
четыре раза в секунду), `room_spectator.avatar_data_url`, `season_ranking_player.nickname`/
`avatar_data_url`, `season_ranking_team.logo_data_url`, `friend_request.from_nickname`/`to_nickname`,
`direct_chat.member_nicknames`/`member_avatars`, `team_invite.owner_nickname`/`team_name`,
`meme_library.owner_name`, `room_invite.room_name`/`from_nickname`, шесть копий каталога мемов внутри
события диверсии. Всё читается через `ProfileDirectoryPort`/`MemeCatalogPort`; вместе с ними исчезает
`propagateNickname` (`ProfileServiceImpl:198-260`), обходивший на каждую смену ника все комнаты игрока.

**Два представления времени.** Все `*_ms bigint` рядом с `timestamptz` (около тридцати колонок) —
наследство документной модели. Остался `timestamptz`; миллисекунды считает DTO, серверное время
отдаёт `GET /api/v2/game/clock`.

**Флаги, слитые в перечисления.** `room.is_test_room`+`team_lobby`+`managed_matchmaking` → `room.kind`;
`room_chat_message.is_test_bot` → `author_seat`; `ranked_result_event.annulled`+`technical` → `outcome`;
`app_user.banned` → `user_ban` + `token_version`; `recording.livekit_status` рядом со `status` → одно
`recording_egress_job.state`, сырой код LiveKit остался только в журнале вебхуков.

**Главное разрезание: `room.phase`.** Одна колонка на 24 символа с восемью значениями, которую сегодня
пишут пять сервисов (`TeamServiceImpl:320`, `MatchmakingServiceImpl:168,473`, `AdminServiceImpl:259`,
`GameServiceImpl:266,705,1570`, `TestBotsServiceImpl:297,392,407,516`), не имеет прямого преемника:
жизненный цикл комнаты ушёл в `room_lifecycle.state`, состояние хода — в `match_turn.state`, факт
завершения партии — в `match.state`. Пока значения двух уровней лежали в одной колонке, «один писатель»
был невозможен физически. Там же раздвоился `room.created_by`: неизменяемое авторство осталось в `room`,
изменяемое хозяйство переехало в `room_host.host_player_id` — сегодня `setup_host_watch` и
`manual_host_transfer` переписывают `created_by`, из-за чего дыра A1 означает буквально захват комнаты.

**Не заведено намеренно.** Таблицы `meme_report` нет: сегодня жалоб на мемы не существует ни в одной
операции аудита, а `delete_meme_alert` — это «админ снял мем». Справочников оружия и патронов нет:
источник правды — `WeaponRegistry` в коде, вторая копия в базе неминуемо разойдётся. Справочник
дивизионов не таблица: девять языков намертво связаны с файлами `public/i18n`. Таблиц билетов на
загрузку и на воспроизведение нет: билет — это строка `meme_asset` в состоянии `PENDING` со сроком,
подписанная ссылка не хранится. Счётчик попыток входа — не строка в PostgreSQL: писать в базу на
каждый неудачный вход означает дать подбору бесплатный способ её нагрузить.

### 6.4 Конкурентный доступ

**`@Version` (оптимистическая блокировка, конфликт → 409 с просьбой перечитать)** — 24 таблицы:
`player_profile`, `ranked_team`, `team_preflight_session`, `room`, `room_lifecycle`, `room_host`,
`room_player`, `room_spectator`, `room_team`, `room_chat_message`, `room_invite`, `matchmaking_room`,
`matchmaking_ticket`, `match`, `match_turn`, `match_appeal`, `match_pause`, `meme`,
`recording_egress_job`, `recorder_session`, `recording_retention`, `usage_traffic_meter`,
`maintenance_lease`, `test_bot_schedule`.

**Без `@Version` намеренно** — горячие строки, где побеждает последняя запись и это верная семантика:
`player_presence` (пинг раз в 30-60 с), `room_presence` (раз в ~10 с на участника),
`room_presence_counter`, `direct_chat_member` (атомарный счётчик), `match_word`, `match_turn_word`,
`match_appeal_vote`, `match_pause_absentee`, `word_submission`, `match_player_ammo`,
`season_*_standing`, `team_preflight_participant` (каждый пишет только свою строку).

**Атомарные счётчики (`UPDATE … SET x = x ± 1`, без чтения в память).** `user_account.token_version`
(сегодня RMW в двух местах — `AuthServiceImpl:267-269` и `AdminServiceImpl:325-327`, и одновременные
бан и смена пароля теряют один инкремент, то есть отозванная сессия остаётся живой);
`room_lifecycle.game_number`; `direct_chat_member.unread_count`; `season_*_standing.points/games/wins`;
`match_player_ammo.amount` (списание `… WHERE amount > 0`: ноль изменённых строк = `NO_AMMO`, проверка
и списание становятся одним оператором); `sabotage_entitlement.free_games_used`;
`match_reward_progress.guessed_total`; `recording_retention.save_count`; `usage_traffic_period.tx_bytes`;
`meme_asset.revision`.

**Условные UPDATE вместо блокировок (rowcount = 0 → 409).** Апгрейд гостя (`WHERE kind='guest'` —
закрывает гонку A4 без чтения перед записью); закрепление дивизиона (`WHERE division_locked_at IS NULL`
→ 409 `DIVISION_LOCKED`, сегодня это правило живёт в двух местах); ротация refresh (`WHERE revoked_at
IS NULL`; ноль строк = реюз → гасим цепочку одним `UPDATE … WHERE family_id=…`); одноразовость ссылки
восстановления; переходы `replacement_clip` (`WHERE state='RECORDING'` — различает «уже готов» и «уже
выброшен», чего `@Version` не умеет).

**Уникальные индексы вместо check-then-act** (сегодня всё это — чтение перед вставкой, дающее 500 на
гонке): ник, почта, имя команды, активный состав игрока, капитан команды, живая заявка в друзья на пару,
шапка чата на пару, активный бан, согласие, `event_key` аналитики, `delivery_id` вебхука,
`(room_id, game_number)` записи и зачёта, прогон ботов в комнате. **Ветка на
`DataIntegrityViolationException → 409` в `GlobalExceptionHandler` обязательна** — сегодня её нет (B7),
и новая схема без неё отдаст те же 500.

**Блокировка строки (`SELECT … FOR UPDATE`)** — там, где остаётся честный read-modify-write:
`room_lifecycle` при старте партии (выделение `game_number`, чтение слов и составов, создание `match` —
иначе две вкладки хозяина заведут две партии с одним номером; сегодня от этого спасает только клиентский
`claimGameTab` в localStorage); `match_turn` на каждом действии со словом (объясняющий против сторожевого
таймера истечения; под этой же блокировкой пропущенному слову назначается `bag_position = max+1`);
`match_saboteur` на выстреле (кулдаун + лимит трёх клипов); `match_effect_lock` на канале эффекта;
`room_host` при передаче по бездействию; `match_appeal` при закрытии; `season_player_standing` при
зачёте (личные очки — разница с `synced_team_points`); `season_team_standing` при техпоражении (штраф
зависит от текущего числа: 15 → 30 → 50); `recording_egress_job` при старте записи (замена колонке
`start_lock_at_ms`); `usage_traffic_meter`; `test_bot_run` на входе в тик.
**Порядок взятия фиксирован**: `room_lifecycle` → `match` → `match_turn` → `match_saboteur` →
`match_effect_lock` (каналы по алфавиту) → остальное; иначе два выстрела по разным каналам дают клинч.

**Внешние вызовы вне транзакций.** LiveKit (`StartWebEgress`, `StopEgress`, `ListEgress`,
`listParticipantIdentities`), S3 и почта — всегда по схеме «короткая транзакция: взять блокировку и
записать намерение → внешний вызов → короткая транзакция: применить результат по `@Version`». Сегодня
`RecordingServiceImpl.startRoomRecording` держит транзакцию поверх сетевого вызова с таймаутом 20 с при
пуле в 10 соединений (B5). Рассылка снимков в `/ws/v2` и письма — только через
`@TransactionalEventListener(AFTER_COMMIT)`; сегодня снимки уходят до коммита, внутри транзакции
пишущего (B1). Ни один инвариант через события не выражается.

---

## 7. Слои и правила

### 7.1 Структура пакета области

```
ru.hothat.<область>
  api/          контроллеры, DTO запроса и ответа, мапперы DTO ↔ доменные value-объекты
  usecase/      класс на сценарий; держит транзакцию и порядок шагов
  domain/       чистые правила без Spring и без БД (политики, реестры, value-объекты)
  port/         интерфейсы, которые область ТРЕБУЕТ от соседей и от внешних систем
  spi/          интерфейсы, которые область ПРЕДОСТАВЛЯЕТ соседям (+ реализация здесь же)
  store/        репозитории и JPA-сущности; классы package-private
```

Наружу видны только `api`-DTO и интерфейсы из `spi`. `store` и `domain` — package-private.
Админские контроллеры живут в `<владелец>.api.admin` (`ru.hothat.recording.api.admin`,
`ru.hothat.media.api.admin`, …): путь называет консоль, пакет — владельца данных.

### 7.2 Контракт слоёв

| Слой | Обязан | Запрещено |
|---|---|---|
| **Controller** | Разобрать DTO, вызвать **один** use-case, вернуть `ResponseEntity<ResponseDTO>` | Любое доменное решение; сборка `Map`; `try/catch`; разбор дат (сегодня `AdminController:48-76`); обращение к репозиторию (сегодня `PublicController`); ручная проверка прав; вызов второго use-case; `switch` по полю тела |
| **UseCase** | Один сценарий — один публичный метод; `@PreAuthorize`; `@Transactional` здесь и только здесь; порядок шагов | Внешний вызов (LiveKit, S3, почта, TTS) внутри транзакции; вызов чужого use-case; `Map<String,Object>` в сигнатуре; возврат JPA-сущности |
| **Domain** | Чистые правила: `RoomAccessPolicy`, `TurnRules`, `WeaponRegistry`, `LoadoutRules`, `FriendshipPolicy`, `NicknamePolicy`, `RoomCleanupPolicy`, `DateRange`, `Seasons`, `DivisionCatalog` | Spring-аннотации; обращение к БД; `System.currentTimeMillis()` (только `Clock`); `new Random()` (только `RandomSource`) |
| **Store** | Единственная дверь в базу; `@Version` на конкурентных сущностях | Быть публичным для другой области; `EntityManager` в обход менеджера (сегодня так делает `DocumentServiceImpl:44`) |

Церемониальные интерфейсы (один impl, все методы `Map`) не создаются: сегодня их ~19, и 114 объявлений
в сервисных интерфейсах имеют тип `Map<String,Object>`.

### 7.3 Как области общаются

Ровно три способа, других нет.

1. **Read-порт** — синхронное чтение чужих данных; интерфейс объявлен в `spi` владельца, возвращает
   маленький `record`. Порты: `RoomMembershipPort`, `RoomStatePort`, `MatchClockPort`,
   `ProfileDirectoryPort` (ник, аватар, дивизион), `FriendshipPort`, `TeamMembershipPort`,
   `PreflightReadinessPort`, `RecordingOwnershipPort`, `MemeCatalogPort`, `SabotageEntitlementPort`,
   `RankingPort`, `LoadoutPort`.
2. **Командный порт** — синхронное изменение чужих данных **внутри текущей транзакции**; реализация
   принадлежит владельцу таблицы, поэтому «единственный писатель» не нарушается: писать продолжает
   владелец, вызывающий лишь просит.
3. **Доменное событие** — `@TransactionalEventListener(AFTER_COMMIT)`; только для того, что вправе
   опоздать: проекции, значки, аналитика, рассылка в `/ws/v2`, письма. **Инварианты событиями не
   выражаются никогда.**

Прямо запрещено: звать чужой use-case, инжектить чужой репозиторий, принимать или возвращать чужую
JPA-сущность, обращаться к чужой таблице по имени. Проверяется ArchUnit: «`ru.hothat.X` не зависит от
`ru.hothat.Y..store`», «…не зависит от `ru.hothat.Y..usecase`», «циклов между областями нет»,
«поле-коллекция DTO аннотировано `@Valid`».

**Девять транзакций, пересекающих границу** (разрыв на события запрещён явно):

| Операция | Что пишет | Механизм |
|---|---|---|
| Регистрация | учётка + карточка + присутствие + согласие | `RegisterAccountUseCase` (auth) → `ProfileCommandPort` |
| Создание комнаты (`app-core.js:12101`) | `room`, `room_lifecycle`, `room_host`, первое место игрока | `CreateRoomUseCase` (room) |
| Вход в комнату (`:12277`, `:4634`) | `room_player` + `room_presence` | `EnterRoomUseCase` (room) |
| Старт партии | `match`, `match_team`, `match_player`, `match_word` + фаза комнаты + арсеналы всех игроков | `StartMatchUseCase` (game) → `RoomLifecyclePort` + `ArsenalCommandPort` |
| Закрытие апелляции | отмена слов + возврат в шляпу + награды + переход хода | `CloseAppealUseCase` (game) → `RewardCommandPort` |
| Обрыв связи | пауза, техзавершение, остановка записи, закрытие комнаты | `ReportDisconnectionUseCase` (game) → `RecordingCommandPort` + `RoomCommandPort` |
| Приглашение в комнату (`SocialServiceImpl:501-580`) | `room_invite` + сообщение переписки + счётчик непрочитанного | `SendRoomInviteUseCase` (room) → `ChatCommandPort` |
| Лобби команды (`TeamServiceImpl:309-352`) | комната, слот команды, место игрока | `EnsureTeamLobbyUseCase` (team) → `RoomCommandPort` |
| Бан игрока | `user_ban` + `moderation_action` + `token_version` + отзыв refresh | `BanUserUseCase` (admin) → `IdentityCommandPort`; `removeParticipant` в LiveKit — после коммита |

**Кто пишет инбокс.** Открытый вопрос предыдущего круга закрыт: владелец `direct_chat_member` — область
`chat`; `room` пишет в него только через `ChatCommandPort.appendRoomInvite(...)` в той же транзакции
(строка 7 таблицы). Отдельного адреса у этого нет и не должно быть.

### 7.4 Где живёт авторизация

`@EnableMethodSecurity`; `@PreAuthorize` — на публичных методах use-case, а на классе контроллера та же
аннотация как декларация для springdoc и для ревью. Роли: `USER`, `GUEST` (новая, закрывает A7),
`ADMIN`, `OWNER` (сегодня выдаётся и не проверяется нигде — получает двух потребителей: реестр учёток и
включение записи в комнате) и пять машинных — `RECORDER_BOOTSTRAP`, `RECORDER`, `EGRESS`, `CRON`,
`MONITOR_AGENT`, каждая своим фильтром, все секреты через `MessageDigest.isEqual`.

Ресурсные предикаты — бины `@roomAuthz`, `@gameAuthz`, `@teamAuthz`, `@preflightAuthz`, `@chatAuthz`,
`@friendAuthz`, `@teamInviteAuthz`, `@roomInviteAuthz`, `@recordingAuthz` в `ru.hothat.security.authz`;
каждый — тонкая обёртка над read-портом владельца с `@RequestScope`-кешем, чтобы SpEL не устраивал
второе чтение комнаты на запрос. Бизнес-правил внутри нет.

`HotHatUser` производит только `PrincipalResolver`; ни одна область не пересчитывает `admin`/`owner`
заново — это устраняет две несовпадающие формулы админа (A5) и семь точек ручной проверки. Матчеры в
`WebSecurityConfig` остаются вторым рубежом (`/api/v2/*/admin/**` не нужен: консоль — это
`/api/v2/admin/**`; плюс `/api/v2/machine/**` с пятью фильтрами), правило по умолчанию —
`.anyRequest().authenticated()`.

**Пять инвариантов владения остаются внутри use-case, и это названо честно**: автор сообщения чата,
автор мема, снятый игрок (`replacement_record_result`), автор клипа, участник записи при сохранении.
На классе стоит более широкий предикат. Причина: каждый из них требует чтения самой строки, а предикат
уровня класса читал бы её вторично. Компенсация — ArchUnit-правило «в `usecase` есть вызов
`требуетАвторства(...)` для помеченных `@OwnershipChecked` методов» и тест на каждый из пяти.

### 7.5 Судьба `GameRules`

`service/game/GameRules.java` (274 строки, 12 публичных методов) — единственный кусок чистых правил в
проекте; сохраняется, но перестаёт быть общей статикой над JPA-сущностями.

| Что сейчас | Куда переезжает |
|---|---|
| `BASE_ARSENAL`, `ARSENAL_KEYS`, `COOLDOWN_MS`, `arsenal()`, `rewardForScore()`, `specialRewardsBetween()`, `sabotageLocks()`, `appendRecentSabotage()` | `ru.hothat.sabotage.domain.WeaponRegistry` — `record Weapon(WeaponType type, String ammoKey, int baseAmmo, long durationMs, boolean lock, boolean advanced, int reward)`. `BASE_ARSENAL` и `ARSENAL_KEYS` **выводятся** из реестра, поэтому их расхождение (сегодня даёт NPE, `GameRules:34`) становится невыразимым. Каталог отдаётся через `GET /api/v2/game/weapons`, фронт выбрасывает свою копию (`app-core.js:356`) |
| `MEME_LOADOUT_SIZE`, `BUILTIN_MEMES`, `normalizedLoadout()`, `MemeQueue`, `memeQueue()`, `grantMemes()`, `applyQueue()` | `ru.hothat.sabotage.domain.LoadoutRules`; встроенные мемы перестают быть хардкодом в трёх местах и становятся посеянными строками `meme` со `slug` |
| `PAUSABLE_PHASES`, `currentTurnDeadline()`, `turnDurationSeconds()` | `ru.hothat.game.domain.TurnRules` |
| `rosterForTeam()`, `allGamePlayers()` | `ru.hothat.room.domain.RosterRules`; доступ для `game` — через `RoomMembershipPort` |
| `testBotIds()` | `ru.hothat.testbots.domain.BotRoster` |
| `Divisions` + список девяти языков (сегодня продублирован трижды) | `ru.hothat.profile.domain.DivisionCatalog`, отдаётся `GET /api/v2/profile/divisions` |

Все переехавшие правила принимают value-объекты, а не `Room`/`RoomPlayer`, — тогда «правило знает про
сущность соседа» перестаёт компилироваться.

---

## 8. Гостевой доступ

`ROLE_GUEST` выдаётся вместе с гостевой сессией (сегодня `PrincipalResolver:36-40` отвергает гостя на
любом защищённом маршруте — A7; это чинится). Роль **не даёт ничего по умолчанию**: правило по умолчанию
— `hasRole('USER')`, поэтому гостю открыто ровно то, где `'GUEST'` назван явно на классе. Матчеров,
открывающих поддерево гостю, нет; расширить поверхность нельзя, не написав `GUEST` в `@PreAuthorize` —
это проверяется грепом.

**Четыре вещи, названные заказчиком, — четыре класса, десять адресов:**

| Что | Класс | Адреса |
|---|---|---|
| Лобби | `LobbyDirectoryController` | `GET /api/v2/lobby/rooms`, `GET /api/v2/lobby/rooms/{roomId}` + канал `/ws/v2/lobby` с кадром `spotlight` (та же проекция живьём) |
| Превью комнаты | `RoomPreviewSessionController` | `POST` \| `PUT` \| `DELETE /api/v2/lobby/rooms/{roomId}/preview-session` |
| Апгрейд | `GuestUpgradeController` | `POST /api/v2/auth/accounts/upgrades` |
| Согласия | `MyConsentController` | `GET` \| `POST /api/v2/profile/me/consents` |

Один вызов `POST …/preview-session` делает то, что сегодня делают два клиентских вызова: заводит место
наблюдателя (`setDoc spectators/{uid}` с `preview:true`, `live-preview.js:101`) и выдаёт видеотокен
только на приём (`POST /api/token` с `preview_session`, `:104`). Публиковать дорожки такой токен не
позволяет, в приватную комнату не пускает.

**Плюс механика сессии, без которой гость не проживёт 15 минут** (это моё решение, а не заказчика, и я
называю его отдельно): `POST /api/v2/auth/sessions/guest`, `POST /api/v2/auth/sessions/renewal`,
`DELETE /api/v2/auth/sessions/current`, `GET /api/v2/auth/me`, `GET /api/v2/auth/me/ban-state`.
Последний адрес — не расширение поверхности, а исправление: сегодня `live-preview.js:99` подписан на
`bans/{uid}` и вышибает забаненного со страницы; без него забаненному гостю в превью никто не скажет,
что он забанен. Тот же ответ приходит кодом `403 BANNED` на heartbeat превью.

**Закрыто гостю явно и проверяемо:** вход в комнату и зрительское место в ней (`RoomEntryController` —
`hasRole('USER')`), любой чат, друзья, команда, подбор, партия, мемы, записи, аналитика
(`AnalyticsEventController` намеренно `hasRole('USER')` — «ничего сверх» понято буквально), профиль
кроме согласий. Гость не имеет карточки, ника в индексе и дивизиона; его место в превью помечено
`preview=true`, не считается зрителем комнаты и не попадает в ростер. В `user_account` он живёт строкой
`kind='guest'` без почты и пароля (CHECK делает «гостя с паролем» непредставимым), а брошенные строки
подметает индекс `(created_at) WHERE kind='guest'`.

---

## 9. WebSocket

`/ws/documents` не существует. Вместо универсального шлюза «путь строкой» — семь именованных каналов,
у каждого пара DTO (hello + event) и правило доступа, принадлежащее области-владельцу, а не транспорту.
Причина не в стиле: `PUBLIC_READS` шлюза включал `rooms/*/wordSubmissions`, то есть слова чужой
команды читались штатным запросом — шлюз по пути не умел резать поля по роли.

> **Состояние.** Шлюз снесён вместе со всем документным стеком (`service/db/**`, `dto/db/**`,
> `DocumentChangedEvent`, документная половина `LegacyChangeBridge`): последний его подписчик — экран
> комнаты — живёт кадром `/ws/v2/room/{roomId}` по контракту `03-room-channel-adapter.md`. Пары
> «документы шлюза ↔ кадр», снятые до сноса, лежат в репозитории фронта
> (`test/fixtures/room-frames/moments/`); повторный прогон `RoomFrameFixtureE2ETest` пишет только кадры
> в `frames/` и золото не трогает.

| Канал | Кому | Что несёт |
|---|---|---|
| `/ws/v2/lobby` | GUEST, USER | Витрину открытых комнат; кадром `spotlight` — состав выбранной комнаты в том же сокете |
| `/ws/v2/room/{roomId}` | участник либо зритель | Комнату, игроков, составы, зрителей, чат, состояние партии — шесть прежних подписок одним каналом |
| `/ws/v2/me/social` | USER | Входящие и исходящие заявки, инбокс, бан; hello-кадр несёт значки шапки портала |
| `/ws/v2/chat/{peerUid}` | друг | Сообщения одной переписки; `pair` вычисляет сервер |
| `/ws/v2/team/{teamId}/preflight` | участник команды | Состояние предматчевой проверки |
| `/ws/v2/media/memes` | USER | Изменения библиотеки мемов |
| `/ws/v2/machine/recorder/rooms/{roomId}` | RECORDER | Зеркало сцены: комната и составы для страницы рекордера |

**Кадр `/ws/v2/room/{roomId}` и экран.** Имена полей в кадре — имена API v2, а экран комнаты живёт
ключами документа. Что переводить и где экран читает старое имя — в [03-room-channel-adapter.md](03-room-channel-adapter.md);
там же — кто какие поля кадра получает (слово хода, своё снаряжение, свои клипы, съёмка Подмены).

**Три служебных кадра, и `unsubscribe` среди них — на этот раз буквально.**

| Кадр | Смысл |
|---|---|
| `{type:"ping"}` → `{type:"pong"}` | Сердцебиение сессии, как сегодня |
| `{type:"spotlight", roomId}` | Переключить превью внутри канала лобби. Прежняя подписка на состав выбранной комнаты, но без нового рукопожатия: `home/home.js:93` ротирует комнату каждые 30 секунд у каждого посетителя главной, включая гостя, — отдельный канал превью означал бы разрыв и проверку токена каждые полминуты |
| `{type:"unsubscribe", id}` | Снять `spotlight`, не закрывая канал. Это точный эквивалент `db.js:389-398`; закрытие сокета кодом 1000 остаётся вторым, более грубым способом, и сервер снимает регистрацию в `afterConnectionClosed` |

**Правила канала.** Токен — в `Sec-WebSocket-Protocol`, не в query (сегодня он в query-строке и оседает
в логах прокси); перепроверка `tokenVersion` раз в 60 секунд, иначе соединение переживает бан и
`logout-all` (A9); `allowedOriginPatterns` — тот же список, что у HTTP (сегодня у WS стоит `"*"`, D8);
проверка прав **до** регистрации подписки (сегодня наоборот, и запрещённая подписка живёт вечно, ломая
записи по этому пути всем остальным — B1); рассылка только из `@TransactionalEventListener(AFTER_COMMIT)`
и вне потока запроса; ошибка канала — та же форма `{type:"error", id, error, code}`, `getMessage()`
произвольного исключения наружу не выпускается (E7).

**Сколько сокетов у клиента.** На главной — один (`lobby`) плюс `me/social` у вошедшего. На игровом
экране — `room/{roomId}` и `me/social`. В переписке — плюс `chat/{peerUid}`. Больше трёх одновременно
не бывает; прежний вариант с отдельным каналом превью давал пять.

**Второй живой транспорт — LiveKit Data — не забыт.** Сегодня партия рассылается ведущим по data-каналу
(`publishGameState`, `livekit.js:1631`), а база — резервный путь (`refreshFollowerRoomSnapshotOnce`).
Типы пакетов: `game-state`, `game-state-heartbeat`, `game-secret` (несёт `currentWord` объясняющему,
`app-core.js:1319`), `sabotage`, `replacement-record-ready`, `replacement-record-failed` и тест-эффекты.
**С переносом движка на сервер три первых типа удаляются в той же задаче**, иначе появится второй
источник правды, гоняющийся с сервером: состояние партии приходит по `/ws/v2/room/{roomId}`, слово —
ответом `POST …/turn` только объясняющему. Data-канал остаётся презентационным: мгновенные эффекты
диверсий и готовность подменного клипа, где задержка важнее достоверности.

---

## 10. Конвенция DTO и Swagger

### 10.1 Именование и форма

- Пакет: `ru.hothat.<область>.api.dto`. Шесть сегодняшних пустых каталогов `dto/` удаляются.
- **Одна операция — одна пара**: `<Действие>RequestDTO` + `<Действие>ResponseDTO`. Переиспользование
  между операциями запрещено. Единственное разрешённое разделение — **композиция**: общий вложенный
  `record` (`RoomSummaryView`, `PlayerCardView`, `TurnView`, `RecordingCardView`, `MemeCardView`)
  включается в несколько ответов как поле. Именно композиция снимает цену дублирования админских и
  игроцких проекций: форма данных описана один раз, различаются обёртки и набор полей.
- Форма — java `record` и для запроса, и для ответа. Списки — всегда объект с курсором
  `{items, nextCursor, limit}`, никогда голый массив (D2). Курсор объявлен для восьми списков: комнаты
  лобби, друзья, чат комнаты, личная переписка, мемы, записи, рейтинги, учётки. Лимиты по умолчанию
  переносятся из сегодняшних порогов: друзья 100, входящие 50, исходящие 80, чат 40, чат комнаты 40,
  мемы 60, записи 50, рейтинги 100, учётки 100 (сегодня 5000 — `ProfileServiceImpl:647`).
- Ответ без тела — `204`; ответ с телом — всегда именованная схема, никогда `Map<String,Object>`.
- **Одна операция — одна форма ответа.** Сегодняшние несовместимые ветки (`matchmake`, `record_result`,
  `sync_game_presence`, `setup_host_watch`, `ranked_autostart`, `recordings start/finish`,
  `send_room_invite`, `test-bots tick`) сводятся к одной записи с полем-перечислением (`state`,
  `outcome`) и nullable-полями. Клиент перестаёт различать ответы по наличию ключа (C6).

### 10.2 Валидация

- Bean-валидация выражает обязательность (`@NotNull`, `@NotBlank`); необязательность — обёрточным типом
  с единственным местом дефолта. `spring.jackson.deserialization.fail-on-unknown-properties=true`
  включается после удаления `room_name` из `livekit.js:541` и `live-preview.js:104`.
- Общие форматы — собственные аннотации: `@RoomId` (`^hat-[0-9a-f]{16}$`), `@Nickname`
  (`^[A-Za-z][A-Za-z0-9_]{2,19}$`), `@ImageDataUrl(max=…)`, `@MemeId`, `@DivisionLanguage`, `@TeamName`.
  `RoomId` — value-тип в сигнатурах use-case: тогда «не проверил идентификатор» перестаёт компилироваться.
  Это же убирает три разных валидатора `room_id`, живущих сегодня в трёх файлах (E6).
- Закрытые наборы — `enum` в DTO: `WeaponType`, `AnalyticsEventType`, `HostActivityKind`,
  `PreflightIntent`, `GameMode`, `RoomKind`, `ChatMessageKind`, `MemeAssetKind`, `LegalDocument`.
  Jackson отвергает неизвестное значение до входа в контроллер — «нет `switch` по строке» выполняется
  буквально.
- Каскадный `@Valid` обязателен на вложенных структурах (сегодня забыт, A8); проверяется ArchUnit.
- Сущности `model/**` не появляются ни в сигнатурах, ни в ответах: `passwordHash` больше не может уехать
  в браузер (B4).

### 10.3 Ошибки

`enum ErrorCode(int status, String text)` — код, статус и текст в одном месте; `ApiException.of(ErrorCode)`
— единственный конструктор (перегрузка `of(String)`, молча дающая 400, удаляется);
`record ErrorDTO(String error, String code, Map<String,Object> details)`. Переменная часть уезжает в
`details`: `{"fields":["memeCycleCursor"]}` вместо `FIELD_UNKNOWN: memeCycleCursor` (E3).
`GlobalExceptionHandler extends ResponseEntityExceptionHandler` (сегодня битый JSON и отсутствующий
параметр дают 500 со стектрейсом — E1) плюс ветка на `DataIntegrityViolationException → 409` (B7).
Дисциплина статусов: 409 — только настоящий конфликт состояния; нарушение предусловия — 422; «нет прав»
— 403 с кодом вида `NOT_A_PLAYER`, а не `PLAYER_NOT_FOUND`; смена своего пароля с неверным текущим —
403, не 401 (иначе клиент уходит в цикл обновления токена, `auth.js:178-195`). Клиент ветвится **только**
по `code`; `errorMessage()` в `app-core.js:7860-7901` переписывается на `switch (error.code)`.

### 10.4 Swagger

1. `@Schema(description, example)` на каждом поле каждого DTO; `allowableValues` для enum, `pattern` для
   форматов; `therapi-runtime-javadoc` в `annotationProcessorPaths`, чтобы javadoc попадал в спецификацию.
2. `@SecurityRequirement(name="Bearer")` — **глобально** в `SwaggerConfig`; публичные операции помечаются
   пустым `@SecurityRequirements`. Состояние по умолчанию совпадает с `.anyRequest().authenticated()`
   (сегодня замка нет у 11 защищённых операций — C11).
3. `OpenApiCustomizer` дописывает во все операции 401/403/500 со схемой `ErrorDTO`; точечные
   `@ApiResponse` — на особых кодах (409 `NICKNAME_TAKEN`, 402 `SABOTAGE_LIMIT_REACHED`,
   410 `ROOM_INVITE_EXPIRED`, 429 `WEAPON_COOLDOWN`, 413 `MEME_TOO_LARGE`).
4. `@Parameter` на каждом `@RequestParam`/`@PathVariable`/`@RequestHeader`; `@RequestParam Map<String,String>`
   не допускается (сегодня так объявлен весь рекордер — C1).
5. **Теги springdoc = поддеревья пути**, один `GroupedOpenApi` на область плюс сводная группа `all`:
   `auth`, `profile`, `friends`, `chat`, `team`, `lobby`, `room`, `game`, `rating`, `media`, `recording`,
   `admin`, `machine`, `app`. Группы `db`/`portal`/`game`/`ops` не заводятся — это имена старых
   диспетчеров. `springdoc.swagger-ui.path=/api/docs`, `tagsSorter=alpha`, `operationsSorter=method`.
6. `/v3/api-docs/**` и `/swagger-ui/**` — под `hasRole('ADMIN')` либо выключены в проде переменной
   `SPRINGDOC_API_DOCS_ENABLED=false` (A13); сегодня открыты анонимно и указывают `servers` на прод.
7. Протокол `/ws/v2/**` — отдельным AsyncAPI-документом со ссылкой через `.externalDocs(...)`: семь
   каналов, три кадра, у каждого пара DTO.

---

## 11. Порядок работ

### 11.1 Главное следствие решения «база пересобирается полностью»

**Переходного слоя нет, и выкатка по частям в прод невозможна.** Старый бекенд читает `app_user`,
`room` (103 колонки) и `room_player`; новый — 92 таблицы с другими именами, другими ключами
(`player_id uuid` вместо `uid varchar(160)`) и другой моделью фазы. Спроецировать новую схему в снимок
`rooms/{id}` для `/api/db/document` нечем, кроме рукописной легаси-проекции, которую пришлось бы
поддерживать всю миграцию и выбросить в конце. Поэтому:

- v2 — **отдельный деплой** со своей базой; v1 продолжает работать со своей до дня переключения;
- фронтенд получает `VITE_API_BASE` и переключается на v2 **целиком**, сначала на dev, потом на прод;
- единица отката — окружение, а не эндпоинт: вернуть `VITE_API_BASE` на v1 и поднять прежний фронт;
- учётки на v2 заводятся заново — это прямо разрешено решением заказчика №3.

Фазы ниже — **порядок разработки**, а не порядок релизов. Каждая фаза заканчивается работающим
вертикальным срезом на dev, и именно он служит приёмкой: тестов в проекте нет, регрессионной базы нет,
golden-снимки `/api/db` не годятся, потому что схема другая.

### 11.2 Фазы

| Фаза | Бекенд | Фронтенд | Как проверяется | Как откатиться |
|---|---|---|---|---|
| **0. Каркас** (1 нед) | Модульная раскладка `ru.hothat.<область>`, Flyway V2 (пустая база), `ErrorCode` + `GlobalExceptionHandler extends ResponseEntityExceptionHandler`, `@EnableMethodSecurity`, девять authz-бинов, пять машинных фильтров, `Clock`/`RandomSource`, ArchUnit, springdoc, CI | Ничего | Сборка, ArchUnit, пустой `/api/v2/app/health` | Ветка не мержится |
| **1. Личность и карточка** (2 нед) | `auth` (13), `profile` (20), `app` (3); таблицы: 4 auth + 5 profile + `feature_flag` + `analytics_event` | `auth.js` целиком, `portal-shell.js`, `account/`, онбординг в `app-core.js:7711-7728`, `features.js` | Регистрация, вход, гость, апгрейд, онбординг, согласия, смена ника, аватар, присутствие — на dev | Dev-база пересоздаётся, ветка откатывается |
| **2. Социальное** (2 нед) | `friends` (9), `chat` (7); каналы `me/social`, `chat/{peerUid}`; таблицы: 2 friends + 4 chat | `friends/friends.js`, `realtime-social.js`, `portal.js` (переписки) | Заявка → принятие → переписка → значок непрочитанного | Ветка |
| **3. Команда и рейтинг** (2 нед) | `team` (12), `rating` (4); канал префлайта; таблицы: 6 team + 5 rating | `portal.js` (команда, префлайт, вкладки рейтингов) | Создание команды, приглашение, префлайт вдвоём, таблицы сезона | Ветка |
| **4. Медиа** (1 нед) | `media` (9), канал `media/memes`; таблицы `meme`, `meme_asset`; сверка бакета наполняет каталог | `meme-s3-only.js`, `meme-upload-ui.js`, `app-core.js` (библиотека, обойма) | Загрузка мема, постер, воспроизведение, обойма из пяти | Ветка; бакет не трогается |
| **5. Комната и лобби** (4 нед) | `room` (34), `lobby` (10); каналы `lobby` (+`spotlight`) и `room/{roomId}`; таблицы: 11 room + 3 lobby | `home/home.js`, `live-preview.js`, `db.js` теряет `setDoc`/`updateDoc`/`deleteDoc`, `app-core.js` — вход, места, составы, чат, приглашения | Создание комнаты, вход, зритель, составы, чат, приглашение, превью гостем, подбор | Ветка; dev-база пересоздаётся |
| **6. Партия и диверсии** (6 нед) | `game` (26); таблицы: 11 game + 10 sabotage; движок на сервере: `beginTurn`, `guessed`, `skipWord`, `endTurn`, `nextTurn`, `saveWords`, `collectWordsAndStart`, `backToSetup` — 16 клиентских транзакций удаляются | `app-core.js`: игровой цикл, `drawRandomWord(bag)` удаляется, `plan.nextWord` берётся из ответа, `game-state`/`game-secret`/`game-state-heartbeat` из LiveKit Data удаляются, оптимистичная отрисовка подтверждается ответом хода | Полная партия вчетвером, пауза, апелляция, диверсии всех 13 типов, техзавершение | Только откат всей фазы |
| **7. Запись и рекордер** (2 нед) | `recording` (8), `machine/recorder` (7) + вебхук Egress; канал рекордера; таблицы: 11 recording | `recording-view.js` (подпись в заголовок, зеркало на канал), `app-core.js` RECORDER_MODE, `portal.js` (библиотека записей) | Партия с записью от начала до MP4 в бакете | Только вместе с фазой 6 |
| **8. Консоль и машины** (2 нед) | `admin` (23), `machine/maintenance` (3), `machine/usage-snapshots` (1); таблицы: 2 moderation + 8 ops + 3 maintenance + 3 test-bots | `admin.js` (`scope=all` → два запроса, `:271` и `:378`), `test-mode.js` | Дашборд, бан, снятие мема, тестовая комната с ботами, cron-уборка | Ветка |
| **9. Переключение и уборка** (1 нед) | Удаление v1-деплоя; `Sunset` не нужен — старых адресов нет | `db.js` удаляется целиком, флаг `VITE_API_BASE` фиксируется | Прогон пяти сценариев на проде | Возврат `VITE_API_BASE` на v1 в течение двух недель |

### 11.3 Что невозможно выкатить по частям

1. **Фаза 1 целиком.** Токен, `player_id` и роли — фундамент всего остального; половина фазы означает
   аккаунт без карточки и возврат `ensureProfile`, то есть ленивое создание карточки на каждом запросе
   (`ProfileServiceImpl:47-83`), из-за которого профиль сегодня и стал вторым писателем всего подряд.
2. **Фаза 6 целиком.** Ход — это `match_turn` + `match_word` + `match_turn_word` + `match_appeal` +
   `match_pause` в одной транзакции под одной блокировкой. Половина хода на сервере, половина в браузере
   не работает ни в какой комбинации: сервер и клиент будут тянуть слово из разных источников. Здесь же
   умирают три типа пакетов LiveKit Data — иначе появится второй источник правды.
3. **Фазы 6 и 7 вместе.** Страница рекордера — тот же `app-core.js` в `RECORDER_MODE`
   (`:28-30`, `:13890`); перенос движка меняет и её. Двенадцать адресов рекордера и двадцать шесть
   адресов партии неделимы.
4. **`room_lifecycle.state` и `match_turn.state` вводятся вместе.** Они вдвоём заменяют `room.phase`;
   пока значения обоих уровней лежат в одной колонке, «один писатель» невозможен физически — а именно
   ради этого режется схема.
5. **Переключение окружения на прод.** Единый момент для всех областей; частичного перехода нет,
   потому что базы разные.

Отдельно: **фаза 5 без фазы 6 даёт работающее лобби и комнату, но не партию.** Это первая точка, где
можно честно остановиться, если объём окажется неподъёмным; тогда v2 остаётся dev-контуром, а прод
живёт на v1 до возобновления работ.

---

## 12. Оценка объёма

| Область | Контроллеры | Use-case | Request-DTO | Response-DTO (с вложенными) | Мапперы | Порты | Сущности + репозитории | Классов |
|---|---|---|---|---|---|---|---|---|
| auth | 6 | 13 | 9 | 13 | 3 | 2 | 8 | ~54 |
| profile | 7 | 20 | 11 | 22 | 4 | 3 | 10 | ~77 |
| friends | 3 | 9 | 3 | 11 | 2 | 2 | 4 | ~34 |
| chat | 2 | 7 | 4 | 9 | 2 | 2 | 8 | ~34 |
| team | 6 | 12 | 5 | 16 | 3 | 3 | 12 | ~57 |
| lobby | 4 | 11 | 4 | 9 | 2 | 3 | 6 | ~39 |
| room | 12 | 34 | 17 | 36 | 6 | 4 | 22 | ~131 |
| game | 11 | 26 | 8 | 28 | 5 | 4 | 22 | ~104 |
| sabotage (без своего поддерева) | 0 | 0 | 0 | 0 | 0 | 3 | 20 | ~23 |
| rating | 2 | 4 | 4 | 9 | 2 | 2 | 10 | ~33 |
| media | 3 | 9 | 6 | 11 | 2 | 2 | 4 | ~37 |
| recording | 3 | 8 | 3 | 12 | 3 | 3 | 22 | ~54 |
| admin | 7 | 23 | 9 | 24 | 5 | 6 | 26 | ~100 |
| machine | 6 | 12 | 6 | 13 | 3 | 4 | 6 | ~50 |
| app | 2 | 3 | 1 | 3 | 1 | 1 | 4 | ~15 |
| realtime | 8 | 10 | 10 | 10 | 2 | 0 | 0 | ~40 |
| **Итого** | **82** | **201** | **100** | **226** | **45** | **44** | **184** | **~882** |

Плюс ~12 доменных политик (`WeaponRegistry`, `LoadoutRules`, `TurnRules`, `RosterRules`,
`RoomCleanupPolicy`, `FriendshipPolicy`, `NicknamePolicy`, `DivisionCatalog`, `DateRange`, `Seasons`,
`RoomAccessPolicy`, `MatchClock`), 9 authz-бинов, 5 машинных фильтров, ~10 событий со слушателями,
конфигурация Swagger и ~25 миграций Flyway. **Итог ≈ 900 классов** против сегодняшних 216 java-файлов
и 19 545 строк.

**Календарь.** Сумма фаз §11.2 — 23 недели при одном исполнителе, то есть **5-6 месяцев**, включая
правку фронтенда внутри тех же фаз. Самая тяжёлая — фаза 6 (6 недель): перенос движка партии,
16 клиентских транзакций и удаление трёх типов пакетов LiveKit Data. Оценка «2-4 недели» из §6 аудита
относилась к переименованию маршрутов и к этому объёму неприменима.

---

## 13. Риски и открытые вопросы

| № | Риск | Рекомендация |
|---|---|---|
| 1 | **Переход на прод — одно событие без частичного отката.** Базы v1 и v2 несовместимы, аккаунты заводятся заново. Если после переключения вскроется дефект в партии, вернуть можно только всё окружение целиком. | Держать v1-деплой включённым и оплаченным две недели после переключения; `VITE_API_BASE` — переменная сборки, а не константа. Переключать в понедельник утром, не в пятницу. Перед переключением — пять сквозных сценариев на dev с реальным LiveKit. |
| 2 | **Идемпотентности хода сегодня нечем выразить: `turnId` в контракте не существует.** `POST …/turn/completion`, `/expiry`, `/next` и `/appeal/closing` объявлены идемпотентными по нему, а двойной клик без него даст два перехода хода. | `match_turn.id` вводится схемой и **обязан приезжать в теле** этих четырёх операций; несовпадение — 409 `TURN_STALE`. Это первое, что пишется в фазе 6, и первое, что проверяется нагрузкой из двух вкладок. |
| 3 | **445 строковых ключей ответа не сверены с фронтом ни разу.** DTO выведены из названий ключей в аудите; ни один не сверен с фактическим набором полей, а обещание «одна форма ответа» без этого непроверяемо. | До начала фазы 1 снять скриптом карту «какое поле какого ответа читает фронт» по вызовам `apiPost`/`api(`/`onSnapshot` (~114 мест) и приложить её к каждому response-DTO как чек-лист. Работа на 1-2 дня, без неё фазы 5-6 нечем принимать. |
| 4 | **Ноль тестов на 19 545 строк, и новая регрессионная база не появляется сама.** Ни одна найденная гонка (потерянное обновление, rollback-only, грязные снимки) не воспроизведена; фазы 5-7 проверять нечем, кроме ручного прогона. | Приёмка каждой фазы — сквозной сценарий Playwright на dev (вход, лобби, комната, партия вчетвером, запись). Плюс модульные тесты на чистую поверхность с первого дня: `WeaponRegistry`, `TurnRules`, `LoadoutRules`, `RoomCleanupPolicy`, `DivisionCatalog` — они пишутся без БД и покрывают именно те правила, где сегодня расхождение даёт NPE. |
| 5 | **Дробность 2,57 эндпоинта на класс при ориентире 3-4; 18 классов с одним эндпоинтом.** Это следствие того, что два требования спорят: «3-4 на класс» и «ровно один уровень прав на класс». Уровней прав 21. | Оставить как есть: права важнее арифметики, а 74 HTTP-класса против 137 в отвергнутом варианте — уже минус 46%. Однооперационные классы (`RoomController`, `MatchStateController`, `RoomHostHandoverController`, `RoomInviteStatusController`, `RoomInviteAcceptanceController`, `RoomRecordingPreferenceController`, `WeaponCatalogController`, `ServerClockController`, `PublicPlayerController`, `PublicTeamController`, `GuestUpgradeController`, `OwnerUserRegistryController`, `MemeFileController`, `RecordingPlaybackController`, `RecorderBootstrapController`, `EgressWebhookController`, `UsageSnapshotAgentController`, `AnalyticsEventController`) — это ресурсы с уникальным уровнем прав; сливать их означало бы вернуть дефект прошлого круга. |

**Что осталось непроверенным и должно быть проверено до начала работ.** Форма ответа рекордера
(`RecorderStateServiceImpl:165-225`, 47 ключей) разведена здесь на три адреса — `scene`, `room-state`,
`current-word`, — но построчно не сверена с тем, какие ключи читает `recording-view.js`, опрашивающий
сцену четыре раза в секунду. Это единственное место плана, где разрез сделан по названиям режимов, а не
по наблюдению; полчаса чтения двух файлов снимут вопрос, и сделать это надо до фазы 7.
