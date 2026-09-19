# HOT-HAT — аудит API бекенда

Корень бекенда: `/Users/user/IdeaProjects/hot-hat-back`. Все пути ниже — относительно него,
если не указано иное (пути фронтенда даны от `/Users/user/IdeaProjects/hot-hat`).
Объём: 19 545 строк в `src/main/java`, 18 файлов в `controller/`, 25 сервисных интерфейсов,
26 реализаций, 12 репозиторных фасадов, 39 Spring Data репозиториев, `src/test` не существует.

---

## 1. Резюме

Бекенд — не REST API, а RPC-поверхность, дословно перенесённая из прежних Vercel-функций: 36 объявленных
маппингов, за которыми прячется ~131 логическая операция, потому что семь точек входа ветвятся по строковому
полю тела (`action`) или по флагам query. Слои формально четырёхэтажные (контроллер → интерфейс → impl →
Getter/Saver → Spring Data), но два из пяти уровней ничего не выражают: 114 объявлений в сервисных интерфейсах
имеют тип `Map<String, Object>`, вторых реализаций нет ни у одного интерфейса.

Пять вещей, которые важнее остальных:

1. **Контракта не существует.** `@Schema` — 0 вхождений, `@ApiResponse` — 0. Двадцать из 38 методов
   контроллеров объявлены как `ResponseEntity<Map<String,Object>>`, 13 из 14 POST принимают сырую `Map`.
   445 ключей ответа живут строковыми литералами внутри impl'ов.
2. **Дыры в правах на клиентском документном API.** `POST /api/db/write` не проверяет членство в комнате:
   любой вошедший переписывает `rooms/{id}` (включая `createdBy` — то есть становится хозяином), команды,
   чат и `memeLibrary`, и может их удалить.
3. **Транзакционная модель сломана в двух местах**: снимки WebSocket рассылаются до коммита, синхронно,
   внутри транзакции пишущего; «оптимистичная блокировка» по `updatedAt` — это check-then-act без `@Version`
   и без блокировки строки.
4. **Права размазаны**: декларативных правил ровно два (`hasRole("ADMIN")` на два матчера), всё остальное —
   ручные `if` в четырёх стилях внутри сервисов и контроллеров; `@EnableMethodSecurity` нет.
5. **Ноль тестов** при полном отсутствии типизированного контракта — то есть переписывание сегодня
   невозможно верифицировать ничем, кроме ручного прогона фронтенда.

Хорошее, что стоит сохранить: `GameRules` (274 строки чистых правил), `CollectionRegistry`/`DocumentPath`/
`DocumentAccessGuard` (закрытый реестр коллекций и строгая схема с `400 FIELD_UNKNOWN`), `PrincipalResolver`
как единственная точка превращения токена в личность, `ObjectStorageService` как настоящий шов к S3,
единый `ErrorDTO` + `GlobalExceptionHandler`, разделение репозиториев на Getter/Saver.

---

## 2. Как всё устроено сейчас

### 2.1 Пакеты и слои

```
controller/        18 файлов, 1209 строк  — маршрутизация + диспетчеры switch(action)
config/            WebSecurityConfig, JwtAuthFilter, PrincipalResolver, CurrentUser,
                   ApiException, ErrorMessages, GlobalExceptionHandler, HotHatUser,
                   HotHatProperties, NoStoreHeadersFilter, WebSocketConfig, swagger/, s3/
dto/               11 подпакетов, 18 классов; ШЕСТЬ подпакетов пусты:
                   dto/portal, dto/game, dto/admin, dto/monitor, dto/recording, dto/token
service/           18 доменов; 25 интерфейсов ↔ 26 impl (второй реализации нет ни у одного)
repository/        12 интерфейсов Getter*/Saver* → 6 package-private менеджеров → 39 *Repo
model/             JPA-сущности; ассоциаций (@OneToMany/@ManyToOne) нет ни одной, 23 JSONB-поля в Room
```

Контроллеры действительно тонкие по объёму (27–137 строк), но не по ответственности: в них живут
доменные решения — разбор диапазона дат (`AdminController.java:48-76`), правило занятости ника
(`PublicController.java:52-58`, единственный контроллер, инжектящий репозиторий `GetterUser` напрямую),
фильтр событий вебхука (`RecordingStateController.java:48-50`), алиас имён поля (`TtsController.java:33`),
нормализация языка и дефолт размера комнаты (`PortalController.java:103-105`).

### 2.2 Шаблон «один URL — switch по action»

Унаследован от Vercel-функций и задокументирован в javadoc: `GameController.java:22-25` — «Один POST на
все игровые действия — ровно как было в Vercel-функции… менять контракт при переезде нельзя».

| Точка входа | Веток `case` | Имён действия | Файл |
|---|---|---|---|
| `POST /api/portal` | 42 | 44 (два case со сдвоенными метками) | `controller/PortalController.java:47-121` |
| `POST /api/game` | 17 | 17 | `controller/GameController.java:44-63` |
| `POST /api/recordings` | 11 | 11 | `controller/RecordingsController.java:42-61` |
| `POST /api/admin` | 5 | 5 | `controller/AdminController.java:87-95` |
| `POST /api/media` | 5 | 5 | `controller/MediaController.java:47-54` |
| `POST /api/test-bots` | 4 | 4 | `controller/TestBotsController.java:47-53` |
| `GET /api/recording-state` | 7 режимов по query-флагам | 7 | `service/recording/impl/RecorderStateServiceImpl.java:39-140` |

Итого 93 логических операции за семью адресами. Конверт ответа собирается в трёх несовместимых стилях:
`ok(key,value)` (`PortalController.java:124-129`), `okAll(payload)` (`:131-136`) и «сервис сам положил `ok`»;
`GameController.java:65-67`, `MonitorController.java:38-41`, `CleanupController.java:62-64`,
`RecordingStateController.java:51-54` дописывают `put("ok", true)` + `putAll(...)` поверх результата сервиса.

### 2.3 Генерический документный API + WebSocket

`GET /api/db/document?path=`, `POST /api/db/query`, `POST /api/db/write` (`controller/DocumentController.java`)
воспроизводят прежнюю модель Firestore: путь строкой (`rooms/{id}/players/{uid}`), пакет операций в одной
транзакции вместо `writeBatch`, `expectedUpdatedAt` вместо `runTransaction`, маркеры
`{"__op":"serverTimestamp"}` / `{"__op":"increment","by":N}`. Реестр коллекций закрытый
(`service/db/CollectionRegistry.java:24-45`, 14 шаблонов), права — `service/db/DocumentAccessGuard.java`
(перенесённые `firestore.rules`), схема строгая (`400 FIELD_UNKNOWN`, `DocumentServiceImpl.java:250-259`).

Особенность: этот срез **обходит слой репозиториев** — `DocumentServiceImpl` работает напрямую с
`@PersistenceContext EntityManager` (`service/db/impl/DocumentServiceImpl.java:44`) и склеенным JPQL (`:76-100`).
Живые обновления идут по `/ws/documents` (`service/db/impl/DocumentSocketHandler.java`): токен в query-параметре,
три типа сообщений (`subscribe`/`unsubscribe`/`ping`), ответ — полный снимок. Это вторая входная точка API:
без DTO, без bean-валидации (`QueryRequestDTO` собирается через `MAPPER.convertValue`, `:91`), со своим
форматом ошибки (`{type:"error", error:"<КОД>"}`) и вне OpenAPI целиком.

### 2.4 Авторизация

Личность выдаёт `config/PrincipalResolver.java` (единственная точка; ею пользуются и `JwtAuthFilter`,
и рукопожатие WebSocket). Дальше права размазаны на три уровня:

- **Матчеры** (`config/WebSecurityConfig.java:47-69`): permitAll-список, два правила `hasRole("ADMIN")`
  (`:67-68`), всё остальное — `.anyRequest().authenticated()` (`:69`).
- **Внутриметодные гарды**: `CurrentUser.of/admin` (`controller/CurrentUser.java:13-26`), приватный
  `requireAdmin` (`RecordingsController.java:64-69`).
- **Ручные `if` в сервисах**: `properties.isOwnerEmail(user.email())` (`ProfileServiceImpl.java:586,643`),
  `user.owner()` (`MediaServiceImpl.java:182`), свой `isOwner` (`GameServiceImpl.java:73`),
  `requireHost`/`requirePlayer` (`GameServiceImpl.java:59-71`), четыре яруса в `TokenServiceImpl.java:72-129`.

`@PreAuthorize`/`@Secured`/`@EnableMethodSecurity` — 0 вхождений. `ROLE_OWNER` выдаётся
(`JwtAuthFilter.java:56-57`) и не проверяется нигде.

### 2.5 Обработка ошибок

`config/ApiException.java` (RuntimeException + `int status`; перегрузка без статуса молча даёт 400, `:23-25`),
`config/GlobalExceptionHandler.java` (`@RestControllerAdvice`, шесть обработчиков) и единый
`dto/common/ErrorDTO.java` — `record ErrorDTO(String error, String code)`. В коде 326 вызовов
`ApiException.of(...)` со 174 различными кодами; словарь текстов `config/ErrorMessages.java` содержит
70 записей, `resolve` на неизвестном коде возвращает сам код (`:107`).

`GlobalExceptionHandler` не наследует `ResponseEntityExceptionHandler`, поэтому штатные исключения Spring MVC
(отсутствующий `@RequestParam`, битый JSON, 405, 415) попадают в `@ExceptionHandler(Exception.class)` (`:70-74`)
и отдают **500 INTERNAL** с ERROR-стектрейсом в логе.

### 2.6 Что сейчас производит Swagger

`config/swagger/SwaggerConfig.java` объявляет Info, `servers` с боевым `${URL_BACK}` и схему Bearer.
Размечено: 38 `@Operation`, 17 `@Tag`, 8 `@SecurityRequirement` (на классах Admin, Analytics, Game, Portal,
Recordings, TestBots, Token, Tts). Не размечено ничего: `@Schema` 0, `@ApiResponse` 0, `@Parameter` 0,
`@ResponseStatus` 0, свойств `springdoc.*` в `application.properties` нет.

Практический результат: спецификация показывает ~36 операций (вместо ~131), у большинства — тело и ответ
как безымянный `object`, ни одного описанного кода ошибки, `ErrorDTO` не привязан ни к одной операции.
`/v3/api-docs/**` и `/swagger-ui/**` открыты анонимно (`WebSecurityConfig.java:65`).

### 2.7 Сводка по контроллерам

| Контроллер | Адреса (методов) | Логических операций | Тип запроса | Тип ответа | DTO? | Swagger | Валидация |
|---|---|---|---|---|---|---|---|
| `AuthController` | `/api/auth/**` (12) | 12 | `@Valid <DTO>` ×10, без тела ×2 | `AuthResponseDTO`/`MessageDTO`/`UserProfileDTO` | да (11 файлов) | `@Operation`, **нет** `@SecurityRequirement` у 4 защищённых | bean, дублируется в сервисе |
| `TokenController` | `POST /api/token` (1) | 1 (+ветвление по `role`) | `Map<String,Object>` | `Map<String,Object>`, 201 | нет (`dto/token` пуст) | `@Operation`+`@SecurityRequirement`, схем нет | нет; `Ids.requireRoomId` вручную |
| `DocumentController` | `/api/db/**` (3) | 3 + 3 типа WS-сообщений | `@RequestParam` / `@Valid QueryRequestDTO` / `@Valid WriteRequestDTO` | `DocumentDTO`, `List<DocumentDTO>` | да (3) | `@Operation`, **нет** `@SecurityRequirement` | bean частично (каскад `where` забыт) |
| `PortalController` | `POST /api/portal` (1) | **44** | `Map<String,Object>` | `Map<String,Object>` | нет (`dto/portal` пуст) | одна `@Operation` на 44 | нет |
| `GameController` | `POST /api/game` (1) | **17** | `Map<String,Object>` | `Map<String,Object>` | нет (`dto/game` пуст) | одна `@Operation` на 17 | только `Ids.requireRoomId` |
| `RecordingsController` | `POST /api/recordings` (1) | **11** | `Map<String,Object>` | `Map<String,Object>` | нет | одна `@Operation` на 11 | нет |
| `RecordingStateController` | `/api/recording-state`, `/api/recording-egress` (2) | **8** | `@RequestParam Map<String,String>` + `Map` | `Map<String,Object>` | нет | `@Operation`, параметры не описаны | нет |
| `MediaController` | `/api/media` ×2, `/api/meme-library-sync`, `/api/optimize-meme` (4) | 8 | `@RequestParam` / `Map` / нет тела | `Void`(302), `Map`, `MemeLibrarySyncResponseDTO` | 1 из 8 | `@Operation`, **нет** `@SecurityRequirement` | нет |
| `AdminController` | `GET/POST /api/admin` (2) | 6 | `@RequestParam` ×3 / `Map` | `Map<String,Object>` | нет (`dto/admin` пуст) | `@Operation`+`@SecurityRequirement` | нет |
| `MonitorController` | `GET/POST /api/monitor` (2) | 3 | `@RequestParam`/`Map`+заголовок | `Map<String,Object>` | нет (`dto/monitor` пуст) | `@Operation`, **нет** `@SecurityRequirement` | нет |
| `CleanupController` | `/api/cleanup-rooms`, `/api/cleanup-recordings` (2 `@RequestMapping{GET,POST}`) | 4 операции OpenAPI | `Map`+`Authorization` | `Map<String,Object>` | нет | `@Operation`, **нет** `@SecurityRequirement` | ручной `cleanRoomId` |
| `TestBotsController` | `POST /api/test-bots` (1) | 4 | `Map<String,Object>` | `Map<String,Object>` | нет | `@Operation`+`@SecurityRequirement` | `Ids.requireRoomId` |
| `AnalyticsController` | `POST /api/analytics` (1) | 1 | `Map<String,Object>` | `Map<String,Object>`, 201 | нет | `@Operation`+`@SecurityRequirement` | нет |
| `TtsController` | `POST /api/tts` (1) | 1 | `Map<String,Object>` | `Map<String,Object>` | нет | `@Operation`+`@SecurityRequirement` | нет |
| `PublicController` | `/api/geo`, `/api/nickname-available` (2) | 2 | `@RequestHeader`/`@RequestParam` | `Map<String,Object>` | нет | `@Operation` | нет |
| `HealthController` | `GET /api/health` (1) | 1 | нет | `Map<String,Object>` | нет | `@Operation` | — |
| `FeatureController` | `GET /api/features/{name}` (1) | 1 | `@PathVariable` | `FeatureFlagDTO` | **да** | `@Operation` + выведенная схема | нет |

---

## 3. Карта операций

Ниже — все найденные логические операции. Столбец «вход» перечисляет фактически читаемые поля,
«выход» — фактически кладущиеся ключи. Ключ `ok` добавляется конвертом контроллера или сервиса
(см. §2.2) и в перечислениях не повторяется.

### 3.1 Авторизация и токены (`controller/AuthController.java`, `TokenController.java`)

| Операция | Адрес | Вход | Выход | Права |
|---|---|---|---|---|
| Регистрация | `POST /api/auth/register` | `email`, `password`, `nickname` (DTO) + `User-Agent` | `AuthResponseDTO{accessToken, refreshToken, user:UserProfileDTO}` | permitAll |
| Вход | `POST /api/auth/login` | `email`, `password` + `User-Agent` | тот же | permitAll |
| Гостевой вход | `POST /api/auth/guest` | `nickname?` (тело необязательно) | тот же | permitAll; **токен не принимается никаким защищённым маршрутом** |
| Привязка гостя | `POST /api/auth/link` | `uid`, `email`, `password` | тот же | permitAll, владение не проверяется |
| Обновление пары | `POST /api/auth/refresh` | `refreshToken` | тот же | доказательство — сам токен |
| Выход | `POST /api/auth/logout` | `refreshToken` | `MessageDTO` | permitAll |
| Выход везде | `POST /api/auth/logout-all` | — | `MessageDTO` | authenticated, owner-only по построению |
| Запрос сброса | `POST /api/auth/password-reset` | `email` | `MessageDTO` (всегда одинаковый) | permitAll |
| Подтверждение сброса | `POST /api/auth/password-reset/confirm` | `token`, `password` | `MessageDTO` | токен из письма (письма нет) |
| Смена пароля | `POST /api/auth/password` | `currentPassword`, `password` | `MessageDTO` | authenticated |
| Изменение имени | `PATCH /api/auth/profile` | `displayName` | `UserProfileDTO` | authenticated |
| Профиль | `GET /api/auth/me` | — | `UserProfileDTO{uid,email,displayName,nickname,guest,admin,divisionLanguage,…}` | authenticated |
| Токен LiveKit | `POST /api/token` | `room_id`, `role`, `participant_identity`, `preview_session`, `participant_name` (клиент шлёт ещё `room_name` — не читается) | `server_url`, `participant_token`, `turn{urls,username,credential,expires_at,ttl_seconds}` | authenticated + 4 яруса в `TokenServiceImpl:72-129`, 9 кодов отказа |

### 3.2 Портал — профиль и присутствие (`service/portal/impl/ProfileServiceImpl.java`)

| Действие (`action`) | Вход | Выход | Права |
|---|---|---|---|
| `record_consent` | поля согласия из тела | `ok` | authenticated |
| `presence_ping` | — | `serverNow` | authenticated |
| `presence_summary` | — | сводка онлайна | authenticated |
| `ensure_profile` | — | 11 ключей, включая вложенный `sabotage` | authenticated |
| `set_division` | `divisionLanguage` | `divisionLanguage` | authenticated; повтор → `DIVISION_LOCKED` 409 |
| `set_ui_language` | `uiLanguage` | `uiLanguage` | authenticated; невалидное молча подменяется |
| `set_nickname` | `nickname` (trim в контроллере) | `nickname` | свой профиль; `NICKNAME_INVALID` 400 / `NICKNAME_TAKEN` 409 |
| `sync_room_nickname` | `roomId` | `{skipped,nickname}` **или** `{nickname,touched}` | свои записи в комнате |
| `set_avatar` | `avatarDataUrl` (≤120000 симв.) | `ok` | свой профиль; `AVATAR_INVALID` 400 |
| `request_nickname` | `nickname`, `reason` (обрезка до 500) | `ok` | authenticated; запись `SupportRequest` никем не читается |
| `consume_sabotage_game` | `roomId`, `gameNumber` | остаток лимита | authenticated; `SABOTAGE_LIMIT_REACHED` 402 |
| `public_player_profile` | `uid` | `profile{…}` (3 уровня вложенности) | любой вошедший видит любого |
| `list_users` | — | `users[]` с e-mail до 5000 профилей | **owner-only внутри сервиса** (`:643`) |
| `repair_nickname_indexes` / `migrate_nicknames` | — | статистика починки | **owner-only внутри сервиса** (`:586`) |

### 3.3 Портал — друзья и переписка (`service/portal/impl/SocialServiceImpl.java`)

| Действие | Вход | Выход | Права |
|---|---|---|---|
| `friends` | — | четыре массива: друзья, входящие, исходящие, статусы | свой uid |
| `add_friend` | `nickname` **или** `friendUid` (взаимоисключающие) | `ok` | `FRIEND_SELF` 400, `ALREADY_FRIENDS`/`REQUEST_EXISTS` 409 |
| `answer_friend` | `requestId`, `accept` | `ok` | получатель заявки |
| `remove_friend` | `friendUid` | `ok` | своя пара; `TEAMMATE_MUST_REMAIN_FRIEND` 409 |
| `social_threads` | — | список тредов | свой uid |
| `get_chat` | `peerUid` | `messages[]` (+условные поля приглашения) | `assertFriend` → `FRIEND_REQUIRED` 403 |
| `send_chat` | `peerUid`, `text` (обрезка до 800), `attachment{kind:image|recording,…}` | `ok` | `assertFriend`; для записи — `RECORDING_NOT_SAVED` 403 |
| `mark_chat_read` | `peerUid` | `ok` | `assertFriend` |
| `send_room_invite` | `roomId`, `friendUid` | `{alreadyInside}` **или** `{inviteId,roomId,roomName}` | хозяин комнаты + дружба |
| `room_invite_statuses` | `inviteIds[]` (обрезка до 60) | `statuses[]`: `pending\|accepted\|expired\|room_missing\|room_closed\|game_started\|missing\|forbidden` | стороны приглашения |
| `accept_room_invite` | `inviteId` | состояние входа | получатель; `ROOM_INVITE_EXPIRED` 410 и др. |
| `promote_room_spectator` | `roomId`, `spectatorUid` | `ok` | хозяин, фаза `setup` |

### 3.4 Портал — команды и префлайт (`service/portal/impl/TeamServiceImpl.java`)

| Действие | Вход | Выход | Права |
|---|---|---|---|
| `my_team` | — | `team{…}`, `preflight{…}` (пять вложенных структур, четыре nullable) | своя команда |
| `create_team` | `name`, `partnerNickname`, `logoDataUrl` (≤280000) | команда | `ALREADY_IN_TEAM` 409 и 7 других кодов |
| `accept_team` / `decline_team` | `inviteId` (+флаг выводится из имени действия в контроллере) | `ok` (собирается в контроллере, метод сервиса `void`) | приглашённый |
| `public_team_profile` | `teamId` | `team{members[],rankings{…}}` (4 уровня) | любой вошедший |
| `ensure_team_lobby` | — | лобби | активная команда |
| `team_preflight_start` | `gameMode`, `intent`, `roomId` | `ok` + «расплющенные» 21 ключ `session` | активная команда, оба лоадаута |
| `team_preflight_media` | `mediaOk`, `teamId?` | `mediaOk`, `serverNow` | участник префлайта |
| `team_preflight_ready` | `ready` | `ok` + те же 21 ключ | активная команда; `MEDIA_NOT_READY` 409 (TTL 25 с) |
| `team_preflight_cancel` | — | `ok` | активная команда |
| `team_preflight_status` | — | `{team, session}` | активная команда |

### 3.5 Портал — матчмейкинг и рейтинги (`MatchmakingServiceImpl.java`, `RatingServiceImpl.java`)

| Действие | Вход | Выход | Права |
|---|---|---|---|
| `matchmake` | `ranked`, `gameMode`, `gameLanguage`, `maxPlayers` | успех: `roomId,count,ready,created,hostUid,deadline,gameMode,gameLanguage,divisionLanguage,teamSize,maxPlayers`; отказ: `roomId,count,ready,failed,deadline` | authenticated; ranked → капитан + готовый префлайт |
| `ranked_join_room` | `roomId`, `gameMode` | `roomId`, `ready` | капитан, совпадение с намерением префлайта |
| `cancel_matchmake` | `roomId` | `ok` | вычёркивает только свой uid; владение не проверяется |
| `cleanup_rooms` | — | `deleted` | **никаких проверок**: сигнатура `cleanup()` не принимает пользователя |
| `ratings` | `mode`, `season`, `year`, `division` (все с молчаливыми дефолтами) | таблицы рейтингов | authenticated |
| `record_result` | результат партии | `{recorded:false}` **или** `{recorded,rankingId,annulled}` **или** `{recorded,rankingId,divisionLanguage,technical,culpritTeamIds}` | участник рейтинговой команды комнаты |

### 3.6 Игра (`POST /api/game`, `service/game/impl/GameServiceImpl.java`)

| Действие | Вход | Выход | Права |
|---|---|---|---|
| `prepare_game` | `room_id`, `order[]` (2..5), `assigned{}` | состояние партии | HOST_ONLY + игрок; фаза `setup`/`finished` |
| `ranked_autostart` | `room_id` | три разные формы | участник комнаты (**не хозяин**), ranked-комната |
| `setup_host_activity` | `room_id`, `kind` (не ограничен списком) | `{active}` | HOST_ONLY, но проверка фазы идёт раньше |
| `manual_host_transfer` | `room_id`, `target_uid` | `ok` | HOST_ONLY, цель активна ≤5 мин |
| `setup_host_watch` | `room_id` | пять разных форм | **только authenticated**; метод меняет `createdBy` |
| `randomize_teams` | `room_id` | составы | игрок комнаты (**без** HOST_ONLY) |
| `kick_player` | `room_id`, `target_uid` \| `targetUid` | `ok` | HOST_ONLY; `KICK_SELF_FORBIDDEN` 409 |
| `set_recording_preference` | `room_id`, `record_game` | `recordGame` | **OWNER_ONLY** (по e-mail) + игрок |
| `sync_game_presence` | `room_id`, `reason` (свободная строка) | семь разных форм | участник партии; ветка `livekit-disconnected` → `deleteRoomTree` |
| `toggle_pause` | `room_id`, `paused` | состояние паузы | HOST_ONLY, фаза из `PAUSABLE_PHASES` |
| `replace_meme_slot` | `room_id`, `slot_index` (0..4), `meme_id` | лоадаут | игрок партии, не активная команда |
| `sabotage` | `room_id`, `type` (13 значений), `meme_id?`, `clip_id?`, `x?`, `y?` | `event{…}` (~18 ключей) | игрок партии, не активная команда; 13 кодов отказа |
| `replacement_record` | `room_id`, `target_uid` | слот клипа | sabotage-режим, ≥11 с хода, лимит 3 |
| `replacement_record_result` | `room_id`, `clip_id`, `ready` | статус клипа | **только тот, кого снимали**; фаза не проверяется |
| `replacement_discard` | `room_id`, `clip_id` | `ok` | **только автор клипа**; фаза не проверяется |
| `appeal_vote` | `room_id`, `word_id`, `vote` | голоса вызывающего | участник, не команда прошедшего хода, фаза `appeal` |
| `finalize_appeal` | `room_id` | две формы | любой участник партии; начисляет очки и арсенал |

### 3.7 Документный API и живые обновления

| Операция | Адрес / сообщение | Вход | Выход | Права |
|---|---|---|---|---|
| Чтение документа | `GET /api/db/document?path=` | `path` (≤4 сегмента) | `DocumentDTO{path,id,exists,data}` — `data` = сериализованная JPA-сущность | `checkRead` по коллекции |
| Запрос коллекции | `POST /api/db/query` | `path`, `where[]{field,op,value}`, `orderBy`, `descending`, `limit`(1..500, деф. 200) | `List<DocumentDTO>` (голый массив) | `checkQuery`; `friendRequests`/`directChats` требуют self-фильтр |
| Пакет записи | `POST /api/db/write` | `operations[]{op:set\|update\|delete, path, data, expectedUpdatedAt}` (≤100) | `List<DocumentDTO>` (для `delete` элемент не добавляется) | `checkWrite`; для `rooms`/`teams`/`chat`/`wordSubmissions`/`memeLibrary` ветка **пустая** |
| Рукопожатие | `GET /ws/documents?token=` | access-JWT в query | 101 / 403 без тела | `PrincipalResolver`, `allowedOriginPatterns("*")` |
| Подписка | WS `{type:"subscribe",…}` | `id`, `path` \| `query` | `{type:"snapshot", id, document\|documents}` | те же `checkRead`/`checkQuery` |
| Отписка | WS `{type:"unsubscribe"}` | `id` | ответа нет | сессия |
| Пинг | WS `{type:"ping"}` | — | `{type:"pong"}` | сессия |

### 3.8 Медиа, записи, TTS

| Операция | Адрес / `action` | Вход | Выход | Права |
|---|---|---|---|---|
| Редирект на файл | `GET /api/media?path=` | `path` (обязан начинаться на `memes/`) | 302 + `Location`, `Cache-Control: max-age=300` | permitAll |
| `upload_ticket` | `POST /api/media` | `memeId`, `kind`, `mime`, `size`, `divisionLanguage` | presigned PUT | authenticated (гард в контроллере) |
| `playback_ticket` | `POST /api/media` | `path` | presigned GET на 2 ч | **сервисный метод не принимает пользователя** |
| `delete_own_orphan` | `POST /api/media` | `path` | `ok` | владелец по вхождению uid в путь |
| `config_status` | `POST /api/media` | — | имя бакета, endpoint | любой вошедший |
| `migrate_legacy_memes` | `POST /api/media` | тело целиком | статистика | **OWNER_ONLY внутри сервиса**; ходит WebClient'ом по `meme.src` (SSRF-поверхность) |
| Синхронизация библиотеки | `POST /api/meme-library-sync` | — | `MemeLibrarySyncResponseDTO` (4 поля) | authenticated; листинг до 2000 объектов |
| Оптимизация мема | `POST /api/optimize-meme` | `meme_id`, `data_url`, `duration_ms` | результат | ADMIN (матчер + гард) |
| `start` | `POST /api/recordings` | `roomId`, `gameNumber` | три формы: `{skipped,reason}` \| `{started:false,recording}` \| `{started:true,recordingId,egressId}` | игрок комнаты |
| `finish` | `POST /api/recordings` | `roomId`, `gameNumber` | аналогично с `finished` | участник записи или игрок |
| `ready_status`, `status` | `POST /api/recordings` | `recordingId` | 2 или ~10 ключей | участник записи |
| `save` | `POST /api/recordings` | `recordingId` | `ok` | участник записи |
| `list_mine` | `POST /api/recordings` | — | до 100 строк `publicRow` (32 ключа) | свой uid; на каждую — `refresh` в LiveKit/S3 |
| `remove_saved` | `POST /api/recordings` | `recordingId` | `ok` | **проверок нет**; переписывает `expiresAtMs` чужой записи |
| `urls` | `POST /api/recordings` | `recordingId` | `watchUrl`, `downloadUrl`, `expiresAtMs` | сохранена или расшарена |
| `admin_urls` / `admin_list` / `admin_delete` | `POST /api/recordings` | `recordingId`, `division` | админские формы | ADMIN внутри `case` |
| Состояние рекордера | `GET /api/recording-state` (без флагов) | `roomId`, `gameNumber`, `sig` | `roomState` — 47 ключей, включая `players[].avatarDataUrl` до 140000 симв. | HMAC-подпись |
| `word=1` | `GET /api/recording-state` | те же | слово хода | подпись + `requireRecording` |
| `ceremony_done=1` | `GET /api/recording-state` | те же | завершение | подпись; **мутирует**: завершает запись, останавливает Egress |
| `ready=1` / `started=1` | `GET /api/recording-state` | те же | статус | подпись; **мутируют**: пишут в БД, создают `GameRecording` |
| `state=1` | `GET /api/recording-state` | те же | состояние | подпись + `requireRecording` |
| `bootstrap=1` | `GET /api/recording-state` | те же | **выдаёт `recorderToken` (JWT)**, безличный | подпись |
| Вебхук Egress | `POST /api/recording-egress` | query `sig` + тело LiveKit | `{ignored}` или результат | HMAC в контроллере; `signing_key` LiveKit не проверяется |
| Синтез речи | `POST /api/tts` | `text` (≤80), `voice_id` \| `voiceId` | MP3 до 2 МБ в base64 внутри JSON | ADMIN ×2 |

### 3.9 Эксплуатация и служебное

| Операция | Адрес / `action` | Вход | Выход | Права |
|---|---|---|---|---|
| Дашборд | `GET /api/admin` | `scope`, `start`, `end` | 4 ключа верхнего уровня, ~30 вложенных | ADMIN ×2 |
| `close_room` | `POST /api/admin` | `room_id` | `ok` | ADMIN |
| `ban_user` / `unban_user` | `POST /api/admin` | `uid` | `ok` | ADMIN; `unban` не получает вызывающего (нет следа авторства) |
| `delete_meme_alert` | `POST /api/admin` | `meme_id` | `ok` | ADMIN |
| `migrate_meme_media` | `POST /api/admin` | `memeId` (camelCase среди snake_case) | статистика | **OWNER**, видно только в `MediaServiceImpl:182` |
| История мониторинга | `GET /api/monitor` | `start`, `end` | снимки, 4 уровня вложенности | ADMIN только внутри метода (маршрут permitAll) |
| Снимок | `POST /api/monitor` | тело не читается; заголовок `X-Hot-Hat-Monitor-Secret` | форма зависит от способа авторизации | секрет (сравнение `equals`) или ADMIN |
| Уборка комнат | `GET/POST /api/cleanup-rooms` | `room_id`, `sweep` + `Authorization` | `{target,sweep}` | cron-секрет **или любой вошедший**; без `room_id` — ранний выход `{ok:true}` |
| Уборка записей | `GET/POST /api/cleanup-recordings` | `Authorization` | статистика | cron-секрет или ADMIN |
| Событие аналитики | `POST /api/analytics` | `event_type` (6 значений), `payload` (6 полей) | `ok`, 201 | authenticated; 201 и на дубликате |
| Здоровье | `GET /api/health` | — | 10 ключей `env` (какие секреты настроены) | permitAll |
| Подсказка дивизиона | `GET /api/geo` | заголовки `CF-IPCountry`, `X-Country-Code` | дивизион | permitAll |
| Свободен ли ник | `GET /api/nickname-available` | `nickname` | `valid`, `available` | permitAll, без ограничения частоты |
| Фича-флаг | `GET /api/features/{name}` | `name` | `FeatureFlagDTO` | permitAll |
| `setup`/`tick`/`stop`/`fart` | `POST /api/test-bots` | `room_id`, `action`, `event_id` | у `tick` — 11 условных ключей; ключа `ok` нет | ADMIN ×2 + фича `bot_enabled` + host/owner |

---

## 4. Недочёты

Ниже — 86 проверенных находок, сгруппированных по темам; внутри темы порядок по серьёзности.
Формулировки уже учитывают уточнения ревьюера (там, где исходное утверждение не подтвердилось,
об этом сказано явно).

### A. Безопасность и права

**A1 (critical). Через `POST /api/db/write` любой вошедший переписывает и удаляет чужую комнату.**
`DocumentAccessGuard.java:119-121` — ветка `case "rooms", "rooms/*/teams", "rooms/*/wordSubmissions",
"rooms/*/chat", "memeLibrary" -> { }` пуста: ни `requireSelf`, ни проверки членства. Достаточно
`{"op":"update","path":"rooms/hat-…","data":{"createdBy":"<свой uid>"}}` — и вызывающий становится хозяином,
получая `prepare_game`, `kick_player`, `manual_host_transfer`, `toggle_pause` и приглашения
(`SocialServiceImpl.assertRoomHost:487`). Тот же гвард применяется к `op:"delete"`
(`DocumentServiceImpl.java:124-129`), то есть чужую комнату, команды и мемы можно удалить.
Нарушено: авторизация ресурса, инвариант «хозяин задаётся сервером». Цена: захват любой комнаты по `room_id`,
который ходит по чатам как ссылка-приглашение. Делать: перенести проверку членства в guard, а `createdBy`,
`phase`, `ranked`, `isTestRoom`, `testOwnerUid`, `closedAt/closedBy` — в серверные поля; `memeLibrary` —
в `SERVER_ONLY_WRITES`.

**A2 (high). `cleanup_rooms` в портале удаляет комнаты без авторизации.**
`PortalController.java:109` → `MatchmakingService.cleanup()` (`MatchmakingService.java:17`) — сигнатура
не принимает `HotHatUser`, проверять нечего; `MatchmakingServiceImpl.java:490-505` зовёт `deleteRoomTree`.
Аутентификация есть (`CurrentUser.of`), авторизации нет, а гостевой токен выдаётся бесплатно на permitAll.
Реальная цена, которую аудит уточнил: `managedFinished` удаляет managed-комнату сразу после `finished`,
а `RatingServiceImpl.recordResult:141-144` требует существующую комнату — то есть цикл вызовов обнуляет
результаты доигранных рейтинговых матчей до записи MMR. Фронт это действие не зовёт ни разу.
Делать: удалить `case` и метод целиком; полный проход оставить `RoomCleanupService` (у него уже есть
кулдаун 5 мин и корректные пороги).

**A3 (high). Ссылка восстановления пароля пишется в лог и больше никуда.**
`AuthServiceImpl.java:174-188` возвращает ссылку строкой, `AuthController.java:93-96` делает
`log.info("Ссылка восстановления пароля выпущена: {}", link)`. Маршрут permitAll
(`WebSecurityConfig.java:57`), TTL 3600 с, токен в БД хранится хешем — то есть строка лога это
единственная копия открытым текстом (CWE-532). Отправитель в проекте существует (Resend,
`MonitorServiceImpl.java:437-478`), но к этой фиче не подключён; страницы `/reset-password` во фронте нет,
а UI при этом показывает «Письмо отправлено». Делать: вынести `MailService`, отправлять письмо из сервиса,
убрать `log.info`.

**A4 (medium). `POST /api/auth/link` доказывает личность гостя одним `uid` из тела.**
`WebSecurityConfig.java:55-57` (permitAll) + `AuthServiceImpl.java:110-128`. Уточнение ревьюера: приз —
пустая гостевая заглушка, а не чужой прогресс, и `uid` добывается не из `rooms/*/players` (гостя туда
не пускают), а цепочкой «`Guest######` → публичный `/api/nickname-available` → `add_friend` →
`resolveUidByNickname`». Опасны два сценария: гонка в окне регистрации (жертва получает 409, а её живые
токены начинают резолвиться в аккаунт с чужим паролем — `linkGuest` не поднимает `tokenVersion`) и захват
брошенных заглушек. Делать: требовать Bearer или refresh гостя, `uid` брать из токена.

**A5 (medium). Права проверяются вручную в четырёх стилях; декларативных правил два.**
`WebSecurityConfig.java:67-68` — единственные `hasRole("ADMIN")`. Дальше: `CurrentUser.admin` (8 точек),
приватный `requireAdmin` (`RecordingsController.java:64-69`, вычисляется прямо как аргумент вызова на `:51`),
`properties.isOwnerEmail` (`ProfileServiceImpl.java:586,643`), `user.owner()` (`MediaServiceImpl.java:182`),
свой `isOwner` (`GameServiceImpl.java:73`). Формула админа существует в двух версиях —
`PrincipalResolver.java:46-49` (uid‖email) и `AuthServiceImpl.java:249-251` (uid‖email‖владелец). Расхождение
работает fail-closed: владелец видит админку в UI и получает 403. `ROLE_OWNER` выдаётся и не проверяется нигде.

**A6 (medium). Один матчер `authenticated` покрывает 44 действия портала и 17 игры.**
`WebSecurityConfig.java:69`. Owner-only `list_users` (выдаёт e-mail всех аккаунтов) и разрушительный
`cleanup_rooms` лежат на том же маршруте, что `presence_ping`. Настоящая причина, по которой это нельзя
выразить матчером, — форма «один POST + поле `action`», а не ширина интерфейсов.
Отдельно подтверждена пропущенная проверка: `setupHostWatch` (`GameServiceImpl.java:392-473`) — единственный
метод среза без `requireHost`/`requirePlayer`, хотя пишет `room.setCreatedBy`. Уточнение: «забрать хозяйство»
через него нельзя (новым хозяином становится первый активный игрок), но посторонний форсирует передачу
раньше срока и читает состояние чужой публичной комнаты. `randomize_teams` без `requireHost` — не дефект:
кнопка во фронте (`app-core.js:11843-11846`) намеренно не хозяйская.

**A7 (medium). Гостю выдают токен, который отвергает собственная проверка прав.**
`AuthController.java:49` выдаёт полную пару, `PrincipalResolver.java:36-40` отвергает любого гостя.
Итог: 401 `AUTH_REQUIRED` на `/api/db`, `/api/portal`, `/api/game`, `/api/token`, `/api/auth/me`
и на рукопожатие `/ws/documents`. Refresh гостя при этом обновляется бесконечно (`:140-149` гостя не смотрит),
поддерживая иллюзию живой сессии. В OpenAPI ограничение не отражено ни словом.

**A8 (medium). Дыры в `DocumentAccessGuard`: `delete` мимо белого списка, `array-contains` недостижим.**
`checkWrite` получает `operation.getData()`, а у `delete` данных нет → `checkUserWrite` выходит на `:128-130`,
и любой вошедший может снести собственную строку `AppUser` вместе с `passwordHash`, `tokenVersion` и `banned`
(связность `nickname_index`/`refresh_token`/`room_player` после этого не восстановить).
`requireSelfFilter:100-103` принимает `array-contains`, которого не знает `operator()`
(`DocumentServiceImpl.java:292-303`) — ветка мёртвая (уточнение: условие дизъюнктивное, так что
`eq` формально проходит; проблема в том, что `memberUids` — jsonb-список и сравнение всё равно не сработает).
`@Valid` на `QueryRequestDTO.where` забыт (`:23`), поэтому `@NotBlank` на `FilterDTO.field` мёртв.

**A9 (medium). WebSocket проверяет токен один раз и никогда больше.**
`WebSocketConfig.java:39-47` — принципал кладётся в атрибуты сессии; `DocumentSocketHandler.java:128` берёт
его оттуда. Access-токен живёт 15 минут, соединение переживает и истечение, и бан, и `logout-all`
с инкрементом `tokenVersion`. Плюс `setAllowedOriginPatterns("*")` (`:33`) против списка `allowed.origins`
у HTTP, токен в query-строке (логи прокси, Referer) и отсутствие лимита на число подписок (`:95`).

**A10 (medium). Ограничения частоты нет нигде.** По репозиторию 0 упоминаний rate limit/bucket4j/счётчика
попыток. `/api/auth/login` (permitAll, BCrypt cost 10 по умолчанию, `WebSecurityConfig.java:79-81`),
`/api/nickname-available`, `/api/auth/password-reset`. Уточнение: подбор упирается в bcrypt только для
существующих аккаунтов (`AuthServiceImpl.java:74-83` бросает `INVALID_CREDENTIALS` до `matches` — это ещё и
тайминг-оракул перечисления e-mail). Самое весомое здесь — **отсутствие потолка на размер JSON-тела**:
`spring.servlet.multipart.*` на `application/json` не действует, контейнер запускается с `-Xmx512m
-XX:+ExitOnOutOfMemoryError` — одно большое тело роняет процесс вместе со всеми идущими партиями.

**A11 (medium). Жизненный цикл токенов: чистки нет, реюз-детекта нет.**
`SaverUser.deleteExpiredTokens` объявлен (`SaverUser.java:38`) и реализован (`UserManager.java:164-172`),
но не вызывается ни разу — в `ScheduledMaintenance` его нет. Повторное предъявление отозванного refresh
даёт 401, но не гасит цепочку (`revokeRefreshTokensOfUid` + `bumpTokenVersion` уже есть в `applyNewPassword`,
`:260-265`). Общие секреты сравниваются `equals` (`CleanupController.java:68-71`, `MonitorController.java:63`),
хотя `constantTimeEquals` в проекте есть (`RecordingServiceImpl.java:112-115`).

**A12 (low). permitAll на маршрутах, защищённых строкой в теле метода.**
`WebSecurityConfig.java:59-61`: `/api/media` (матчер накрывает и POST со всеми пятью `action`),
`/api/cleanup-*`, `/api/monitor`. Уточнение: обхода авторизации нет — `JwtAuthFilter` работает и на permitAll,
а `CurrentUser.of/admin` внутри метода отвечает 401/403. Проблема в достоверности конфигурации: удаление одной
строки при рефакторинге открывает эндпоинт, и ни один тест этого не заметит (тестов нет вовсе).

**A13 (low). Actuator и springdoc открыты анонимно.**
`WebSecurityConfig.java:65-66` + `management.endpoint.health.show-details=always`
(`application.properties:84`). Экспонированы только `health,metrics` — секретов нет, но наружу уходят
вендор СУБД, путь `/app`, свободное место, имена метрик. Весомее соседнее: `/v3/api-docs` отдаёт анонимно
полную карту API с админскими маршрутами, а `servers` указывает на прод.

### B. Транзакции, консистентность и слой данных

**B1 (critical). Снимки WebSocket рассылаются до коммита, синхронно, внутри транзакции пишущего.**
`DocumentServiceImpl.write` помечен `@Transactional` (`:111`) и публикует события на `:135` до коммита;
слушатель — обычный `@EventListener` (`DocumentSocketHandler.java:103`), а не `@TransactionalEventListener`,
хотя javadoc на `:99-102` утверждает обратное. `onDocumentChanged` (`:105-124`) перебирает все сессии × все
подписки и на каждую делает запрос через `documentService.get/list` (`:135`, `:138`) — с `PROPAGATION_REQUIRED`,
то есть в чужой транзакции и по грязному состоянию. Дополнительно найдено ревьюером: `subscribe` кладёт
подписку в map (`:95`) **до** проверки прав и сохраняет её даже при отказе, поэтому запрещённая подписка живёт
вечно; при следующей записи в этот путь `push` бросает `ApiException`, внешняя транзакция помечается
rollback-only, и `catch (RuntimeException)` на `:141` уже не спасает — `POST /api/db/write` падает
`UnexpectedRollbackException`. То есть один клиент, подписавшийся на чужой путь, ломает записи по этому пути
всем остальным. Делать: `@TransactionalEventListener(AFTER_COMMIT)` + асинхронная рассылка вне потока запроса,
перечитывание в `REQUIRES_NEW`, индекс подписок по пути, проверка прав до регистрации подписки.

**B2 (high). Оптимистичная блокировка по `updatedAt` — это check-then-act.**
`DocumentServiceImpl.save:139-150` читает `find` без `LockModeType`, сравнивает и мержит; `@Version`,
`@Lock`, `PESSIMISTIC` в проекте — 0 вхождений, изоляция READ COMMITTED. Окно узкое (внутри одного
`@Transactional`), поэтому 409 в большинстве случаев срабатывает, но потерянное обновление возможно;
ветка `delete` `expectedUpdatedAt` не проверяет вовсе. Замена Firestore-овского `runTransaction`
строго слабее оригинала. Делать: `@Version` на сущностях `/api/db` или чтение под `PESSIMISTIC_WRITE`.

**B3 (high). У одних и тех же таблиц два независимых пути записи.**
`DocumentServiceImpl` и `DocumentAccessGuard` ходят напрямую в `EntityManager` (`:44`, `:58`), остальной
бекенд — через 12 фасадов Getter/Saver. `DocumentChangedEvent` публикуется ровно в одном месте
(`DocumentServiceImpl.java:132`), поэтому все записи сервисов (`saverRoom.save` в `GameServiceImpl` — девять
мест, `propagateNickname` и десятки других) подписчикам `/ws/documents` невидимы. Уточнение: в живых фазах это
частично перекрыто вторым, намеренным транспортом (LiveKit Data, `app-core.js:1348-1357`), но фаза `setup`
и портальные операции (`randomize_teams`, `kick_player`, `manual_host_transfer`) не покрыты ничем.
Плюс `updatedAt` бампится только в Saver, а `/api/db` его не навязывает — запись без этого поля оставляет
отметку протухшей и ломает `expectedUpdatedAt`.

**B4 (high). JPA-сущности сериализуются прямо в ответ: `passwordHash` уходит в браузер.**
`DocumentServiceImpl.java:189-192` — `MAPPER.convertValue(entity, Map.class)`; `@JsonIgnore` в `model/` — 0.
`AppUser.java:35-36` содержит `password_hash`, рядом `tokenVersion`, `banned`. Фронт читает свой профиль
именно так (`app-core.js:875, 4593, 4707, 4985`), и то же самое приходит на записи и в WS-снимках.
Уточнение: утечка строго «своя» (`requireSelf` на `:69`, запрос коллекции `users` запрещён), вектор —
второго порядка (XSS, HAR, devtools), BCrypt медленный. Но структурная суть в другом: контракт ответа
задан геттерами Hibernate-класса, и следующая колонка уедет клиенту так же молча.

**B5 (high). Блокирующие внешние вызовы внутри `@Transactional` при пуле в 10 соединений.**
`RecordingServiceImpl.startRoomRecording` (`@Transactional`, `:163`) на `:242` зовёт LiveKit Egress;
`GameServiceImpl.syncGamePresence` (`@Transactional`, `:616`) на `:646` и в цикле `:652` ходит в LiveKit;
`LiveKitServiceImpl.twirp` блокирует поток `.block(TIMEOUT)` при `TIMEOUT = 20 c` (`:27`, `:122`).
`spring.datasource.hikari.*` не настроен — дефолт 10 соединений. Уточнение: при отказе соединения падение
мгновенное; 20 секунд удержания даёт подвисший LiveKit. Тот же паттерн шире, чем в находке: `listMine`
делает `refresh` по 100 записям, `status` — на каждый опрос карточки.

**B6 (medium). Ловушка rollback-only: обработанная ошибка превращается в 500.**
`GameServiceImpl.safeFinishRecording:780-786` ловит `RuntimeException` вокруг чужого `@Transactional`
(`RecordingServiceImpl.java:374`). Propagation REQUIRED ⇒ вложенное исключение помечает общую транзакцию
rollback-only, внешний `catch` его глотает, а на коммите летит `UnexpectedRollbackException` → 500.
Уточнение: три из пяти процитированных мест мертвы (`refresh`/`cleanupExpired` сами гасят ошибки внутри),
живая связка одна — при не-benign ошибке StopEgress в двух терминальных ветках `syncGamePresence`.
Делать: `REQUIRES_NEW` на побочные операции.

**B7 (medium). Read-modify-write без защиты.** `ProfileServiceImpl.setNickname:199-206` — проверка-и-вставка
при `nickname_key` PRIMARY KEY: уникальность БД удерживает, но гонка даёт 500 вместо 409 (в
`GlobalExceptionHandler` нет ветки на `DataIntegrityViolationException`). `Room` — 23 JSONB-поля в одной строке
без `@Version` и без `@DynamicUpdate`, каждое действие `/api/game` пишет строку целиком
(`GameServiceImpl.java:1189-1196`), затирая параллельные изменения фазы, паузы, счёта. Уточнение:
арсенал лежит в `RoomPlayer`, а не в `Room`, поэтому «диверсия двух игроков возвращает боезапас» —
неверный пример; теряются `sabotageEvent`, `sabotageLocks`, `replacementRecordings` и любые чужие колонки.

**B8 (medium). Ручной N+1 в читающих действиях, без транзакции.**
`SocialServiceImpl.friends:50-80` не помечен `@Transactional` и в трёх циклах делает до 233 отдельных
обращений `getByUid`; `open-in-view=false`, поэтому каждое берёт соединение заново и читает свой снимок.
То же в `RatingServiceImpl:78,85,119` и `RecordingLibraryServiceImpl.listMine:47`. Образец пакетного
чтения в проекте уже есть — `TeamServiceImpl.memberProfiles`.

**B9 (low). Getter/Saver — соглашение, а не барьер.** За обоими интерфейсами один package-private бин
(`RoomManager.java:27`), сущности отдаются managed, `@PreUpdate`/`@UpdateTimestamp`/`@Version` в модели нет,
поэтому мутация «прочитанной» сущности внутри `@Transactional` уедет в базу без обновления `updatedAt`.
`wrap` (`RoomManager.java:201-208` и пять копий) стирает тип исключения; уточнение: 409 на дубликат ника
теряется не из-за него, а из-за отсутствия обработчика `DataIntegrityViolationException`.
Отдельно: `saveNickname` с присвоенным `@Id` уходит в `merge`, поэтому гонка не падает на PK, а молча
переписывает `uid` в существующей строке.

### C. Контракт: DTO и Swagger

**C1 (high). 86 действий за семью диспетчерами не существуют в спецификации.**
`PortalController.java:39`, `GameController.java:35`, `RecordingsController.java:31`,
`AdminController.java:78`, `MediaController.java:41`, `TestBotsController.java:34` — по одной `@Operation`
на весь switch; `RecordingStateController.java:30` принимает `@RequestParam Map<String,String>`, поэтому
ни `roomId`, ни `sig`, ни семь флагов в схеме не именуются. У всех семи и вход, и выход — `Map<String,Object>`,
то есть в спеке это один summary и две пустые формы `object`.

**C2 (medium). `@Schema` и `@ApiResponse` — по нулю на весь проект.**
Проверено grep'ом. 174 кода ошибок и 12 HTTP-статусов (409 — 116 раз, 403 — 64, 400 — 63, 404 — 42, …)
в спецификации отсутствуют; `ErrorDTO` не привязан ни к одной операции. Уточнение: разметка в целом есть
(38 `@Operation`, 17 `@Tag`), дыра именно в моделях ответа и в контракте ошибок.

**C3 (medium). 20 из 38 методов возвращают `Map<String,Object>`, шесть пакетов `dto/` пусты.**
`dto/admin`, `dto/game`, `dto/monitor`, `dto/portal`, `dto/recording`, `dto/token` — 0 файлов.
445 различных ключей ответа существуют только как строковые литералы; крупнейшие объекты —
`RecorderStateServiceImpl.roomState` (47 ключей, `:165-225`) и `RecordingServiceImpl.publicRow`
(32 ключа, `:664-703`). Компилятор не проверяет ни одного; удаление ключа обнаруживается в проде.

**C4 (medium). Нетипизированность просочилась в доменные интерфейсы.**
114 объявлений `Map<String,Object>` в `service/*/[A-Z]*.java`. `GameService` — 17 из 17,
`TokenService.issue(HotHatUser, Map) : Map` (`:10`) — ни входа, ни выхода. Значит типизацию нельзя ввести
только в контроллерах: контракт собирается в сервисах.

**C5 (medium). 13 сырых `@RequestBody Map` без bean-валидации.** `@Valid` есть 13 раз и только в
`AuthController`/`DocumentController`. Поля читаются через `Json`, который по построению не умеет отказывать:
`str(null)→""`, `num(null,10)→10`, `bool(null)→false` (`util/Json.java:73,101,105`). Уточнение: идентификаторы
и перечисления как раз проверяются ниже по стеку; «тихий дефолт с эффектом» — это булевы (`accept`, `mediaOk`,
`ready`, `record_game`, `paused`) и пара числовых. Отдельная цена по контракту: у 13 эндпоинтов в springdoc
нет схемы запроса.

**C6 (medium). Одна операция — несколько несовместимых форм ответа.**
`matchmake` отдаёт 11 или 5 ключей (`MatchmakingServiceImpl.java:177-183` против `:200-212`),
`sync_game_presence` — семь форм, `setup_host_watch` — пять, `record_result` — три, `start`/`finish`
записей — три. Клиент различает их по наличию ключа (`app-core.js:1018`, `:4804`): добавить ключ безопасно,
убрать — нельзя.

**C7 (medium). Форму ответа собирают контроллеры.** `ok`/`okAll` (`PortalController.java:124-136`),
`putAll` в `GameController.java:65-67`, `MonitorController.java:38-41`, `CleanupController.java:62-64`,
`RecordingStateController.java:51-54`. `TeamService.answerInvite` объявлен `void`, поэтому и ответ,
и семантика действия (`"accept_team".equals(action)`) вычисляются в контроллере (`:89-92`).
Пока конверт живёт в веб-слое, типизированный response-DTO написать нельзя.

**C8 (medium). Непроверяемые касты сырых карт и глушитель ошибок.**
`TeamServiceImpl.java:126-131` — `(Map<String,Object>) preflightState(user).get("session")` внутри
`catch (RuntimeException)` с `log.debug`: любая авария репозитория уезжает клиенту как `preflight: null`.
Тот же каст без catch на `:485`, `:543`, `:563`; ещё один — `MonitorServiceImpl.java:248`.

**C9 (medium). Переиспользование DTO связывает несвязанные операции.**
`RefreshRequestDTO` обслуживает `/refresh` и `/logout` (`AuthController.java:69,76`), `AuthResponseDTO` —
пять входов, `UserProfileDTO` — три роли. `AuthService` при этом протекает сущностью:
`AppUser requireUser(String)` + `UserProfileDTO profileOf(AppUser)` (`AuthService.java:43-46`), из-за чего
порядок вызовов знает HTTP-слой (`AuthController.java:129-130`).

**C10 (medium). У 18 существующих DTO нет ни одного `@Schema`, а каскад `@Valid` в одном месте забыт.**
Конвенция выдержана (11 request-классов Lombok, 7 response-record), пояснения написаны в javadoc
(`UserProfileDTO.java:3-12`, `QueryRequestDTO.java:45` — фактический enum операторов фильтра), но springdoc
javadoc не читает: `therapi-runtime-javadoc` в `pom.xml` нет. Протокол маркеров `{"__op":"serverTimestamp"}`
описан только комментарием `DocumentServiceImpl.java:194-201`.

**C11 (medium). `@SecurityRequirement` стоит на 8 контроллерах из 17.**
Не помечены 11 операций, требующих токена: `AuthController` `/logout-all`, `/password`, `PATCH /profile`,
`GET /me`; все три `DocumentController`; три в `MediaController`; `GET /api/monitor`. В Swagger UI у них
нет замка, «Try it out» молча отдаёт 401. Уточнение: cleanup-ручки из этого списка надо убрать — там
`Authorization` объявлен обычным `@RequestHeader` и работает.

**C12 (medium). Ни одного `@Parameter`.** `path` у `/api/db/document` (синтаксис — центральное понятие
всего документного API), `scope`/`start`/`end` (`AdminController.java:38-40`, формат даты нигде),
`nickname` (правило `^[A-Za-z][A-Za-z0-9_]{2,19}$` клиент угадывает по `valid:false`), `sig`
(единственная аутентификация двух permitAll-маршрутов).

**C13 (medium). WebSocket вне OpenAPI целиком.** Протокол `/ws/documents` описан одним javadoc
(`DocumentSocketHandler.java:24-31`); в `SwaggerConfig.info` о канале не сказано ничего. Через него идут
все живые обновления, и он же обходит bean-валидацию (`:91`).

**C14 (medium). Спецификация и UI открыты и не выключаются из конфигурации.**
`WebSecurityConfig.java:65` + ноль свойств `springdoc.*` в `application.properties`. Уточнение: выключить
можно через переменные окружения (`SPRINGDOC_API_DOCS_ENABLED=false`, `.env` монтируется в
`docker-compose.yml:29-30`) — то есть «не выключено и не задокументировано», а не «невозможно».
Группировки (`GroupedOpenApi`) и сортировки нет.

**C15 (low). Доказанный дрейф контракта.** Фронт шлёт `room_name` в `/api/token`
(`public/livekit.js:541`, `public/live-preview.js:104`), сервер это поле не читает никогда
(`TokenServiceImpl.java:60-78`). Уточнение: вреда нет, сервер сам выводит имя комнаты из `room_id`; ценность
находки — как демонстрация того, что расхождение не обнаруживается ничем.

### D. HTTP-семантика

**D1 (medium). Адрес не называет ресурс, метод не называет операцию.**
93 операции за семью адресами (§2.2). Уточнения ревьюера, которые снимают часть аргументов: GET-кеширование
всё равно запрещено сплошным `Cache-Control: no-store` (`config/NoStoreHeadersFilter.java:21-27`), а часть
прав (9 действий admin/test-bots) матчерами всё же выражена. Остаются: непрозрачность OpenAPI, агрегация
метрик по одному URI, невозможность объявить права по URL для четырёх диспетчеров, ручная диспетчеризация.

**D2 (medium). `/api/db` — документный шлюз вместо ресурсов.** Удаление выражается телом POST
(`DocumentServiceImpl.java:122-131`, `public/db.js:235-237`); ветка `delete` не кладёт элемент в `written`,
поэтому объявленный `List<DocumentDTO>` не соответствует `operations` поэлементно. Ответ query —
голый JSON-массив, куда нельзя добавить курсор без ломающего изменения.

**D3 (medium). 409 стал универсальным «нельзя».** 116 вызовов из 326: `TEAMS_NOT_READY`, `ROUND_NOT_ACTIVE`,
`GAME_PAUSED`, `NOT_ENOUGH_TURN_TIME` — это нарушения предусловий, а не конфликты. Обратный перекос:
`PLAYER_NOT_FOUND` отдаётся с 403 в восьми местах и с 404 в шести; `WEAPON_INVALID` — 400 (`:988`) и 409
(`:1020`); `MEME_NOT_LOADED` — 400/404/403 в одном файле. `AuthServiceImpl.java:214` отдаёт 401 на смене
своего пароля — клиент, который на 401 идёт обновлять токен (`public/auth.js:178-195`), уходит в цикл.

**D4 (medium). Сплошной `no-store` на весь `/api/*`.** `NoStoreHeadersFilter.java:21-27` (исключение —
`/api/media`). Условных запросов нет: ETag/Last-Modified/ShallowEtagHeaderFilter в проекте отсутствуют.
Фронт переизобретает кеш сам (`public/features.js:30`, TTL 30 с). Дороже всего опрос рекордера четыре раза
в секунду (`public/recording-view.js:185`) при ответе с `avatarDataUrl` до 140 000 символов на игрока
(`RecorderStateServiceImpl.java:271`).

**D5 (medium). Пагинации нет.** Потолки зашиты: 100/50/80 (`SocialServiceImpl.java:53,66,77`), 5000
(`ProfileServiceImpl.java:647`), 1000 (`RoomCleanupServiceImpl.java:104`), 250/100/2000 в записях и медиа.
Ни `total`, ни `hasMore`, ни `nextCursor`. Уточнение: `/api/db/query` умеет keyset-пагинацию через
`lt/gt`+`orderBy`, а рейтинги и чаты — осознанные top-N; жёстко режутся именно друзья и заявки.

**D6 (medium). Версионирования нет ни в пути, ни в заголовке.** `/api/v` и `X-API-Version` — 0 вхождений;
`SwaggerConfig.java:33` `.version("1.0.0")` ни на что не влияет. Вместе с нетипизированным контрактом это
значит, что ломающее изменение нечем ни провести управляемо, ни обнаружить.

**D7 (medium). Именование полей несогласованно.** `room_id` (`GameController.java:41`,
`AdminController.java:84`, `/api/token`) против `roomId` (портал, записи); `MediaServiceImpl.java:114-122`
читает camelCase, а `:396-408` — snake_case в одном классе; `TtsController.java:33` вынужден поддерживать
`voice_id` и `voiceId`. Опечатка в стиле не даёт ошибки — `Json` подставляет дефолт.

**D8 (medium). CORS разъезжается между HTTP и WebSocket.** `WebSecurityConfig.java:84-95` — список
`allowed.origins`; `WebSocketConfig.java:33` — `setAllowedOriginPatterns("*")`. Оба канала отдают одни
и те же документы.

**D9 (low). На успех всегда 200; два имеющихся 201 поставлены неверно.**
`TokenController.java:31` (ресурс не создаётся), `AnalyticsController.java:31` (201 и на дубликате —
`AnalyticsServiceImpl.java:38` возвращает `{ok:true}` без вставки). `noContent()`, `created(...)`,
`ACCEPTED` не встречаются ни разу; `Location` — только у редиректа медиа. Уточнение: для RPC-поверхности
с единственным потребителем 200-на-успех идиоматичен, а адресуемых ресурсов, куда ставить `Location`,
в API просто нет.

**D10 (low). GET изменяет состояние.** `CleanupController.java:55` — `@RequestMapping(method={GET,POST})`
на удаление объектов из S3; `/api/recording-state` в трёх режимах из семи мутирует (`ceremony_done`
останавливает Egress, `ready`/`started` пишут в БД и создают строку). Уточнение: сценарий «префетчер или
антивирус выполнит удаление» невозможен — cleanup требует заголовка `Authorization`, а мутирующие режимы
рекордера идемпотентны и защищены проверками фазы. Остаётся невозможность применить инфраструктурные
правила и `sig` в query, оседающий в логах.

**D11 (medium, часть D). Объявленный код ответа расходится с фактическим.**
`ResponseEntity<Void>` у `GET /api/media` при фактическом 302 + `Location` (`MediaController.java:34-38`);
`@ResponseStatus` в проекте 0 вхождений. Отдельная ветка в `CleanupController.java:85-88` подменяет ответ
через `@ExceptionHandler` и в спеке не существует.

### E. Ошибки и валидация

**E1 (medium). 500 INTERNAL вместо 400 на штатных ошибках ввода.**
`GlobalExceptionHandler.java:70-74` ловит `Exception` и не наследует `ResponseEntityExceptionHandler`.
`GET /api/db/document` без `path`, `GET /api/nickname-available` без `nickname`, `GET /api/media` без `path`,
любой битый JSON → 500 + ERROR-стектрейс в логе. Часть этих маршрутов permitAll, то есть шум наводится
анонимно. Тело наружу обезличено, утечки нет. Делать: `extends ResponseEntityExceptionHandler` или
`spring.mvc.problemdetails.enabled=true`.

**E2 (medium). Каталог из 174 кодов не описан нигде, 110 приезжают клиенту машинной строкой.**
`ErrorMessages` содержит 70 записей, `resolve` на неизвестном коде возвращает сам код (`:107`).
Пользователь видит «ROOM_FULL», «TEAM_NAME_TAKEN», «RECORDING_PARTICIPANT_ONLY»,
«TEAMMATE_SABOTAGE_LIMIT_REACHED». Мёртвых записей в словаре четыре (`AUTH_INVALID`,
`REGISTRATION_REQUIRED`, `MEME_ALREADY_USED`, `NOT_ENOUGH_TEAM_SLOTS`).

**E3 (medium). Один код — разные статусы; коды с данными внутри.**
Соответствия «код → статус» нет нигде, перегрузка `ApiException.of(String)` молча ставит 400 (`:23-25`).
`FIELD_UNKNOWN: <поля>` (`DocumentServiceImpl.java:258`), `FIELD_FORBIDDEN: <поле>`
(`DocumentAccessGuard.java:133`), `SERVER_VALUE_UNKNOWN: <op>` (`:220`) уезжают клиенту в поле `code`
как есть; `publicCode` чистит только два префикса (`ErrorMessages.java:110-115`). Уточнение: подставляются
данные самого клиента, утечки схемы нет.

**E4 (medium). Фронтенд разбирает ошибки по человеческому тексту.**
`app-core.js:936-937` кладёт `error` в `message`, `code` в `code`; `errorMessage()` (`:7860-7901`) сравнивает
с машинными кодами именно `message` — 14 веток мертвы, потому что сервер подменил код текстом. Единственная
живая (`SABOTAGE_LIMIT_REACHED`, `:12970`) работает ровно потому, что этого кода нет в словаре сервера.
Уточнение: пользователь сегодня ничего плохого не видит — 11 из 14 текстов совпадают дословно.

**E5 (medium). Bean-валидация есть у 12 эндпоинтов из ~100.** Остальное — ручные проверки, разбросанные
между контроллером (`cleanRoomId`, `parseDay`), утилитами (`Ids.requireRoomId`) и impl'ами (`safeText`,
регулярки `AVATAR` в `ProfileServiceImpl.java:34` и `CHAT_IMAGE` в `SocialServiceImpl.java:32` — почти
дословные копии). Уточнение: пороги паролей совпадают, `WEAK_PASSWORD` через HTTP недостижим; реальная цена —
разные коды у одного правила (`INVALID_NICKNAME` в auth против `NICKNAME_INVALID` в портале, последнего нет
в словаре, а фронт ищет первое написание — `app-core.js:7666`).

**E6 (medium). Три разных валидатора `room_id`.** `Ids.requireRoomId` (`util/Ids.java:44-50`, с
`toLowerCase`, применяется в 3 местах), `RecordingStateController.cleanRoomId:58-64` (без `toLowerCase`),
`CleanupController.cleanRoomId:73-79` (своя регулярка `^[a-zA-Z0-9_-]+$` до 120 символов). В портале,
записях и админке `roomId` не проверяется вовсе — отсюда ключ идемпотентности вида `"-0-<uid>"`
(`ProfileServiceImpl.java:399`).

**E7 (medium). WebSocket отдаёт ошибки в своём формате и выносит наружу `getMessage()` любого
исключения.** `DocumentSocketHandler.java:141-143`. Плюс `MAPPER.convertValue` на `:91` стоит вне try:
кривой `query` вылетает из `handleTextMessage`, Spring закрывает сессию, `db.js:341,351-356`
переподключается и шлёт ту же подписку — цикл переподключений вместо внятной ошибки.

**E8 (medium). Валидация через приватное исключение отключила общий проход уборки.**
`CleanupController.java:73-79` бросает `NoRoomRequested` на пустом `room_id`, локальный `@ExceptionHandler`
(`:85-88`) возвращает `{ok:true, target:{}, sweep:{}}` — и это происходит **до** вычисления `shouldSweep`
(`:45`). Значит вызов без `room_id` (в том числе от крона) не выполняет sweep никогда, а отвечает «ok».
Незаметно только потому, что комнаты параллельно убирает `ScheduledMaintenance`.

**E9 (medium). Лимит размера мема проверяется по числу от клиента и не подписывается.**
`MediaServiceImpl.java:117` — `Math.max(1, Json.num(body.get("size")))`; отсутствующее поле даёт 1.
`presignPut` (`:127`) не задаёт `contentLength`, поэтому S3 размер тоже не ограничивает: за подписанной
ссылкой десять минут и произвольный объём.

**E10 (low). Коэрция `(int) Json.num` в контроллерах.** `PortalController.java:62,104`,
`RecordingsController.java:39`. Уточнение: реально опасен один случай — `consume_sabotage_game`,
где ключ идемпотентности (`roomId-gameNumber-uid`) целиком задаётся клиентом и не сверяется с комнатой;
остальные обезврежены ниже по стеку (`MEME_SLOT_INVALID` 400, `RECORDING_GAME_NUMBER_MISMATCH` 409,
повторная `normalizeSize`).

### F. SOLID и структура кода

**F1 (medium, SRP). `GameServiceImpl` — 1624 строки, 17 сценариев, 9 зависимостей.**
`useSabotage` — 220 строк (`:985-1204`), `finalizeAppeal` — 200, `rankedAutostart` — 167,
`syncGamePresence` — 148. Шесть независимых причин для изменения в одном файле; `deleteRoomTree` живёт
внутри метода присутствия (`:678-686`). Уточнение: контроллер уже тонкий, а `Map<String,Object>` — осознанное
решение по совместимости; швы для тестов есть (конструкторная инъекция), нет только швов внутри `useSabotage`.

**F2 (medium, OCP). Новое оружие правится в 13+ точках.**
`ALLOWED_SABOTAGE` (`:43-45`), шесть булевых флагов (`:990-995`), список `advanced` (`:1018`), лестница
`ammoKey` (`:1074-1079`), `switch` длительности (`:1130-1141`), блокировки (`:1145-1152`), обогащение
`event` (`:1154-1177`), `GameRules.BASE_ARSENAL`/`ARSENAL_KEYS` (`:13-18`), `specialRewardsBetween`
(`:61-72`), два продублированных списка редких наград (`GameServiceImpl.java:1496,1522`),
`SocialServiceImpl.java:36` (третий литерал стартового арсенала, уже разошедшийся с `GameRules`),
`RecorderStateServiceImpl.java:329`, `TestBotsServiceImpl.java:35,43,580-582`. Пропуск записи в
`ARSENAL_KEYS` тихо теряет боезапас; ключ в `ARSENAL_KEYS` без пары в `BASE_ARSENAL` даёт NPE
(`GameRules.java:34`).

**F3 (medium, SRP). Портал: пять жирных сервисов с одним потребителем.**
`ProfileService` — 21 метод (один мёртвый: `require(String)`), `SocialService` — 13 (друзья + чат +
приглашения + перевод зрителя в игроки, 823 строки, 9 зависимостей), `TeamService` — 14 (команда + автомат
префлайта, плюс `record TeamContext`/`PreflightContext` объявлены прямо в интерфейсе).
`SocialServiceImpl` дублирует построение `RoomPlayer` (`:731`, `:795`) и держит собственную
`BASE_ARSENAL_ON_JOIN` (`:35-36`), расходящуюся с `GameRules.BASE_ARSENAL`. Уточнение: игрового расхождения
сегодня нет — старт партии безусловно перезаписывает арсенал (`GameServiceImpl.java:136,246`).

**F4 (medium, ISP). `RecordingService` — 17 методов, максимум использования 5.**
Шесть потребителей, четыре метода не зовутся снаружи вообще. `RECORDING_TTL_MS` объявлена в интерфейсе
(`RecordingService.java:11`) — политика хранения в публичном контракте. `RecorderStateService.handle(String,
int, Map<String,String> query)` протаскивает формат HTTP в доменный интерфейс.

**F5 (medium, SRP). Две реализации «уборки брошенных комнат» с разными правилами.**
`MatchmakingServiceImpl.cleanup:490-505` (7 минут, 100 комнат, без grace и кулдауна) против
`RoomCleanupServiceImpl` (10/6 минут, 1000 комнат, grace 2 мин, кулдаун 5 мин).

**F6 (medium, DIP). Доменные интерфейсы принимают сырое тело HTTP.** 26 методов вида
`f(HotHatUser, Map<String,Object> body)`; 515 вызовов `Json.*`. Обратно: `util/Ids.java:47` и
`S3ObjectStorageService.java:63` знают HTTP-статусы. Уточнение: `ApiException` — обычный RuntimeException
с `int status`, а таблица «код → текст» централизована в `ErrorMessages`; децентрализован только статус,
и расходится он у четырёх кодов.

**F7 (medium, DIP). Правило владельца размазано по семи точкам, правило админа существует в двух версиях.**
`HotHatUser` несёт готовые `admin`/`owner`, но потребители считают заново: `ProfileServiceImpl.java:97,355,
586,643`, `GameServiceImpl.java:73`, `MediaServiceImpl.java:182`. `TeamServiceImpl.java:444` синтезирует
принципала за другого игрока с `admin=false, owner=false` — сегодня безвредно (метод читает только uid
и email), но это поддельный субъект в коде.

**F8 (medium, DIP/OCP). db-срез: политика в трёх параллельных switch.**
`DocumentAccessGuard.checkRead:66-75`, `checkQuery:86-94`, `checkWrite:114-122` плюс `CollectionRegistry` —
новая коллекция правится в четырёх местах. `config/WebSocketConfig.java:12` — единственный во всём проекте
импорт из пакета `.impl.`.

**F9 (medium). Сервисные интерфейсы 1:1 с impl.** 25 интерфейсов, 26 реализаций, ни одной второй,
`src/test` нет. Мёртвый код в контрактах: `JwtTokenService.getEmail`, `HotHatUser.emailLower`,
`ProfileService.require`. Уточнение: шесть интерфейсов (`DocumentService`, `ObjectStorageService`,
`JwtTokenService`, `FeatureFlagService`, `AuthService`, `RankedWordService`) типизированы и осмысленны —
претензия относится к ~19 остальным.

**F10 (medium). Ни времени, ни случайности подменить нельзя.** 124 обращения к `System.currentTimeMillis()`/
`Instant.now()` в `service/**`, бина `Clock` нет; `util/Shuffle.java:11` — статический `SecureRandom`;
`Seasons.now()` читает системные часы (`util/Seasons.java:19-24`), а от него зависит `rankingId`.
`WebClient`/`HttpClient` пересобираются на каждый вызов (`MediaServiceImpl.java:346`,
`MonitorServiceImpl.java:323,469`, `EdgeTtsService.java:99`) — при том что образец есть
(`LiveKitServiceImpl.java:41`).

**F11 (medium). Наибольший fan-in: по 9 зависимостей.** `AdminServiceImpl.java:34`,
`SocialServiceImpl.java:38`, `GameServiceImpl.java:47`; плюс скрытые полевые `@Value`:
`RecordingServiceImpl` — семь (`:55-74`), `MonitorServiceImpl` — шесть. По конструктору не видно,
что класс требует ещё семь переменных окружения.

**F12 (medium). `RecordingServiceImpl` обходит собственные швы.** Держит `ObjectStorageService`
и `LiveKitService` и при этом читает их секреты своими `@Value` (`:55-74`), собирая `s3UploadConfig()`
(`:275-283`). Уточнение: передача сырых ключей — требование протокола Egress, а не самовольство; настоящая
проблема — отсутствие точки расширения (`ObjectStorageService.egressUploadConfig()`) и переиспользование
`LIVEKIT_API_SECRET` как ключевого материала своих подписей (ротация ключа инвалидирует все выданные
ссылки на просмотр записей).

**F13 (low). Контроллеры держат доменные правила.** `AdminController.java:48-76` (28 строк разбора
диапазона), `PublicController.java:27,52-58` (единственный контроллер с репозиторием),
`CleanupController.java:73-79` и `RecordingStateController.java:58-64` (дублируют друг друга и
`Ids.requireRoomId`), `TtsController.java:33`, `PortalController.java:103-105`. Уточнение: часть претензий
снята — `maxPlayers` и `Divisions.normalize` повторно нормализуются в сервисе, `Cache-Control` — законная
ответственность веб-слоя.

**F14 (low, OCP). 42 ветки switch в портале.** Уточнение: добавление действия стоит трёх файлов, из которых
два — обычная слоёнка Spring, неустранимая и в реестре хендлеров; ссылка на `CollectionRegistry`
как на готовое решение некорректна. Остаточный риск — строка действия не проверяется компилятором.

**F15 (medium, LSP). Интерфейсы неоднородны.** `void answerInvite` среди тринадцати `Map`-методов
(`TeamService.java:16`); `MediaService.playbackTicket(Map)` — единственный метод без `HotHatUser`,
и ровно он единственный без проверки владения (`MediaService.java:15`).

### G. Эксплуатация и тесты

**G1 (medium). Ноль тестов на 19 545 строк.** `src/test` не существует, `spring-boot-starter-test`
объявлен (`pom.xml:113`). Уточнение к первоначальной формулировке: дизайн как раз мокируем (конструкторная
инъекция, 37 интерфейсов), барьер для первого теста низкий; по-настоящему не воспроизводятся только
статический `SecureRandom` и абсолютные записываемые таймстемпы. Чистая поверхность, тестируемая
без подготовки, — `GameRules`, весь `util/`, `DocumentPath`, `CollectionRegistry`, `DocumentAccessGuard`
(~700+ строк). CI уже гоняет `mvn clean package` (`.github/workflows/deploy.yml`), surefire подхватит.

**G2 (medium). Шов почты отсутствует.** Единственный отправитель — приватный метод
`MonitorServiceImpl.sendReportEmail:437-486`. `SupportRequest` из `request_nickname` не читается вообще
нигде: `SupportRequestRepo` — голый `JpaRepository`, коллекции нет в `CollectionRegistry`.

**G3 (low). Мелкая небрежность в ops-срезе.** `MonitorController.java:50` присваивает неиспользуемое `body`;
лимит 100 продублирован в `CleanupController.java:64` и `ScheduledMaintenance.java:39`;
`FeatureFlagServiceImpl` кеширует по произвольной строке из публичного запроса в `ConcurrentHashMap`
без ограничения размера; осиротевший javadoc в `MonitorService.java:15`.

---

## 5. Целевая архитектура

### 5.1 Ресурсная раскладка вместо action-switch

Портал:

| Сейчас | Целевой эндпоинт |
|---|---|
| `ensure_profile` | `GET /api/v1/me` |
| `set_nickname`, `set_ui_language` | `PATCH /api/v1/me` |
| `set_avatar` | `PUT /api/v1/me/avatar` |
| `set_division` | `PUT /api/v1/me/division` |
| `record_consent` | `POST /api/v1/me/consents` |
| `request_nickname` | `POST /api/v1/me/nickname-requests` → 201 |
| `presence_ping` / `presence_summary` | `PUT /api/v1/me/presence` → 204 / `GET /api/v1/presence` |
| `public_player_profile` | `GET /api/v1/players/{uid}` |
| `consume_sabotage_game` | `POST /api/v1/rooms/{roomId}/games/{n}/sabotage-entitlement` |
| `friends` | `GET /api/v1/me/friends` |
| `add_friend` | `POST /api/v1/me/friend-requests` → 201 + `Location` |
| `answer_friend` | `POST /api/v1/me/friend-requests/{id}/accept` \| `/decline` |
| `remove_friend` | `DELETE /api/v1/me/friends/{uid}` → 204 |
| `social_threads` | `GET /api/v1/me/chats` |
| `get_chat` | `GET /api/v1/me/chats/{peerUid}/messages?limit&cursor` |
| `send_chat` | `POST /api/v1/me/chats/{peerUid}/messages` → 201 |
| `mark_chat_read` | `PUT /api/v1/me/chats/{peerUid}/read` → 204 |
| `send_room_invite` | `POST /api/v1/rooms/{roomId}/invites` → 201 |
| `room_invite_statuses` | `GET /api/v1/room-invites?ids=…` |
| `accept_room_invite` | `POST /api/v1/room-invites/{id}/accept` |
| `promote_room_spectator` | `POST /api/v1/rooms/{roomId}/spectators/{uid}/promote` |
| `my_team` | `GET /api/v1/me/team` |
| `create_team` | `POST /api/v1/teams` → 201 + `Location` |
| `accept_team` / `decline_team` | `POST /api/v1/team-invites/{id}/accept` \| `/decline` |
| `ensure_team_lobby` | `POST /api/v1/me/team/lobby` |
| `public_team_profile` | `GET /api/v1/teams/{teamId}` |
| `team_preflight_*` | `POST` / `GET` / `PATCH …/media` / `PATCH …/ready` / `DELETE` на `/api/v1/me/team/preflight` |
| `matchmake` | `POST /api/v1/matchmaking/tickets` → 201 + `Location` |
| `cancel_matchmake` | `DELETE /api/v1/matchmaking/tickets/{roomId}` → 204 |
| `ranked_join_room` | `POST /api/v1/rooms/{roomId}/players` |
| `ratings` | `GET /api/v1/rankings?mode&season&year&division&limit&cursor` |
| `record_result` | `POST /api/v1/rooms/{roomId}/result` |
| `list_users` | `GET /api/v1/admin/users` (под существующим `hasRole("ADMIN")`) |
| `repair_nickname_indexes` / `migrate_nicknames` | `POST /api/v1/admin/maintenance/nickname-indexes` |
| `cleanup_rooms` | **удалить** — дубль `/api/cleanup-rooms` |

Игра (всё под `/api/v1/rooms/{roomId}`):

| Сейчас | Целевой эндпоинт |
|---|---|
| `prepare_game`, `ranked_autostart` | `POST …/game` → 201 |
| `randomize_teams` | `POST …/teams/randomize` |
| `kick_player` | `DELETE …/players/{uid}` → 204 |
| `manual_host_transfer` | `PUT …/host` |
| `setup_host_activity`, `setup_host_watch` | `PUT …/host/heartbeat` |
| `toggle_pause` | `PUT …/game/pause` |
| `sync_game_presence` | `PUT …/players/me/presence` |
| `set_recording_preference` | `PUT …/recording-preference` |
| `replace_meme_slot` | `PUT …/players/me/loadout/{slotIndex}` |
| `sabotage` | `POST …/sabotages` → 201 |
| `replacement_record` | `POST …/replacement-clips` → 201 + `Location` |
| `replacement_record_result` | `PUT …/replacement-clips/{clipId}` |
| `replacement_discard` | `DELETE …/replacement-clips/{clipId}` → 204 |
| `appeal_vote` | `PUT …/appeal/votes/{wordId}` |
| `finalize_appeal` | `POST …/appeal/finalize` |

Записи и медиа: `POST /api/v1/rooms/{id}/games/{n}/recording`, `POST …/finish`, `GET …/status`,
`GET /api/v1/me/recordings`, `PUT|DELETE /api/v1/me/recordings/{id}`, `GET /api/v1/recordings/{id}/urls`,
админские — `/api/v1/admin/recordings/**`; `POST /api/v1/media/upload-tickets`,
`POST /api/v1/media/playback-tickets` (с `HotHatUser` и проверкой владения), `DELETE /api/v1/media/orphans`,
`GET /api/v1/admin/media/config`, `POST /api/v1/admin/media/migrations`.
Рекордер: `GET /api/v1/rooms/{id}/games/{n}/recorder-state` (только чтение) + `POST …/recorder/ready`,
`POST …/recorder/started`, `POST …/recording/finish`; подпись перенести из query в заголовок.

### 5.2 Конвенция DTO

- Пакет: `ru.hothat.dto.<срез>` — шесть пустых каталогов уже размечены.
- **Одна операция — одна пара**: `<Действие>RequestDTO` + `<Действие>ResponseDTO`. Переиспользование
  между операциями запрещено (общее подмножество — композицией, как `AuthResponseDTO.user`).
- Форма: java `record` и для запроса, и для ответа. Валидационные аннотации на компонентах записи работают.
- На каждом поле — `@Schema(description=…, example=…)`; закрытые наборы — `allowableValues`; форматы —
  `pattern`. Плюс `therapi-runtime-javadoc` в `annotationProcessorPaths`, чтобы уже написанный javadoc
  попал в спеку.
- Bean-валидация выражает обязательность (`@NotNull`/`@NotBlank`), а не отсутствие проверки; необязательность —
  обёрточным типом с явным дефолтом в одном месте. Обязательно `spring.jackson.deserialization.
  fail-on-unknown-properties=true`, иначе опечатка в имени поля по-прежнему молчит.
- Общие форматы — собственные аннотации: `@RoomId`, `@Nickname`, `@ImageDataUrl(max=…)`, `@MemeId`.
  `RoomId` лучше сделать value-типом и принимать его в сигнатурах сервисов — тогда «не проверил»
  перестанет компилироваться.
- Сущности `model/**` не появляются ни в сигнатурах сервисов, ни в ответах. `DocumentDTO.data`
  заменяется проекциями по одной на коллекцию из `CollectionRegistry`.
- Единый стиль имён — camelCase, через `spring.jackson.property-naming-strategy`; snake_case там,
  где нужна совместимость, — точечными `@JsonProperty`/`@JsonAlias`.

### 5.3 Контракт слоёв

```
Controller       только маппинг: разбор DTO → вызов use-case → ResponseEntity<ResponseDTO>.
                 Ноль доменных решений, ноль сборки Map, ноль try/catch, ноль обращений к repository.
UseCase-сервис   один сценарий = один метод. Принимает HotHatUser + RequestDTO, возвращает ResponseDTO.
                 Держит транзакцию и порядок шагов. Внешние вызовы (LiveKit, S3, почта) — ВНЕ транзакции.
Domain-сервис    чистые правила без БД и без Spring: GameRules (расширить реестром оружия
                 record Weapon(type, ammoKey, durationMs, lock, advanced, baseAmmo)), RoomAccessPolicy
                 (нынешние requireHost/requirePlayer), FriendshipPolicy, NicknamePolicy, DateRange.
Repository       единственная дверь в базу. Getter/Saver сохраняются, но начинают что-то гарантировать:
                 либо detached-проекции, либо @Version/@PreUpdate на сущностях. DocumentServiceImpl
                 переезжает на GenericDocumentRepository рядом с остальными менеджерами.
Порты            Clock, RandomSource, MailService, ObjectStorageService (+egressUploadConfig()),
                 LiveKitService (+signPayload/webhookKey/publicUrl), TtsService.
```

Церемониальные интерфейсы (один impl, все методы `Map`) удаляются: ~19 штук в портале, игре, записях,
админке, мониторинге. Остаются те шесть, за которыми чужая система или package-private реализация.

### 5.4 Базовый уровень Swagger

1. `@Bean OpenApiCustomizer` дописывает во все операции ответы 401/403/500 со схемой `ErrorDTO`;
   `ErrorDTO` регистрируется в `components.schemas`.
2. `@SecurityRequirement(name="Bearer")` объявляется **глобально** в `SwaggerConfig`
   (`.security(List.of(...))`), а публичные операции помечаются пустым `@SecurityRequirements`.
   Тогда состояние по умолчанию совпадает с `.anyRequest().authenticated()`.
3. Точечные `@ApiResponse` на операциях с особыми кодами (409 `DOCUMENT_CONFLICT`,
   402 `SABOTAGE_LIMIT_REACHED`, 429 `WEAPON_COOLDOWN`, 410 `ROOM_INVITE_EXPIRED`).
4. `@Parameter` на каждом `@RequestParam`/`@PathVariable`/`@RequestHeader`;
   `@RequestParam Map<String,String>` заменяется явными параметрами.
5. `GroupedOpenApi` по срезам: auth, db, portal, game, media, ops. `springdoc.swagger-ui.path=/api/docs`,
   `tagsSorter=alpha`, `operationsSorter=method`.
6. `/v3/api-docs/**` и `/swagger-ui/**` — под `hasRole("ADMIN")` либо выключены в проде
   переменной `SPRINGDOC_API_DOCS_ENABLED=false` (записать в `.env.example`).
7. Протокол `/ws/documents` — отдельным AsyncAPI-документом со ссылкой через `.externalDocs(...)`.

### 5.5 Контракт ошибок

```java
enum ErrorCode { ROOM_NOT_FOUND(404, "Комната не найдена."), … }   // код + статус + текст в одном месте
record ErrorDTO(String error, String code, Map<String,Object> details) {}
```

- `ApiException.of(ErrorCode)` — единственный конструктор; перегрузка `of(String)` удаляется, чтобы 400
  не появлялся по умолчанию незаметно. Код без текста перестаёт компилироваться.
- Переменная часть уезжает из кода в `details`: `{"fields":[…]}`, `{"player":"Вася"}` вместо
  `FIELD_UNKNOWN: memeCycleCursor`.
- Дисциплина статусов: 409 — только настоящий конфликт состояния (`GAME_ALREADY_STARTED`,
  `NICKNAME_TAKEN`, `ALREADY_IN_TEAM`, расхождение `expectedUpdatedAt`); нарушение предусловия — 422;
  «нет прав» — 403 и код вида `NOT_A_PLAYER`, а не `PLAYER_NOT_FOUND`; смена своего пароля с неверным
  текущим — 403, не 401.
- `GlobalExceptionHandler extends ResponseEntityExceptionHandler`: 400/405/415 сохраняют форму `ErrorDTO`
  (`PARAM_REQUIRED`, `BODY_INVALID`, `PARAM_INVALID`), 4xx логируются на `warn`,
  `Exception.class` остаётся только для настоящих 500. Добавляется ветка
  `DataIntegrityViolationException → 409`.
- Клиент ветвится **только** по `code`; `error` — исключительно для показа. `errorMessage()` в
  `app-core.js:7860-7901` переписывается на `switch (error.code)`.
- WebSocket отдаёт ту же форму: `{type:"error", id, error:<текст>, code:<код>}`; `getMessage()`
  произвольного исключения наружу не выпускается.

---

## 6. План работ

Порядок продиктован риском: сначала то, что чинится в один-два файла и закрывает дыру, потом фундамент
(тесты, транзакции), и только затем массовое переписывание контракта.

### Фаза 0 — экстренные заплатки (1–2 дня, ничего не ломает)

| Что | Где |
|---|---|
| Проверка членства в комнате в `checkWrite` + белый список полей `rooms`; `memeLibrary` → `SERVER_ONLY_WRITES`; `delete` для `users` запретить | `service/db/DocumentAccessGuard.java:119-130` |
| Удалить `case "cleanup_rooms"` и `MatchmakingService.cleanup()` | `PortalController.java:109`, `MatchmakingService*.java` |
| Убрать `log.info` со ссылкой восстановления | `AuthController.java:93-96` |
| `@JsonIgnore` на `passwordHash`/`tokenVersion` (временно, до проекций) | `model/user/AppUser.java:35-52` |
| `requirePlayer` в `setupHostWatch`; `HotHatUser` + проверка владения в `playbackTicket` | `GameServiceImpl.java:392`, `MediaService.java:15` |
| Требовать токен гостя на `/api/auth/link`; отказ в `refresh` гостю | `AuthServiceImpl.java:110-149`, `WebSecurityConfig.java:56` |
| `@Valid` на `QueryRequestDTO.where`; потолок размера JSON-тела; `MessageDigest.isEqual` для двух секретов | `dto/db/QueryRequestDTO.java:23`, `CleanupController.java:68`, `MonitorController.java:63` |
| `springdoc` выключить в проде переменной; `/v3/api-docs` под ADMIN | `.env.example`, `WebSecurityConfig.java:65` |
| Порядок в `CleanupController`: убрать `NoRoomRequested` | `CleanupController.java:44,73-88` |

**Что ломается на фронте:** ничего. Единственный риск — если какой-то клиентский путь писал в `rooms/*`
поля, ставшие серверными; проверить надо ~30 обращений через `roomRef` в `public/app-core.js`
(например `:12082`) и `public/db.js`. Перед выкаткой — прогнать список записываемых полей и внести
в белый список те, что реально нужны клиенту.

### Фаза 1 — фундамент под изменения (3–5 дней)

1. `Clock` бином, `RandomSource` портом; `Seasons.now()` → `Seasons.at(Instant)`.
2. Первые тесты на уже чистой поверхности: `GameRules`, `util/**`, `DocumentPath`, `CollectionRegistry`,
   `DocumentAccessGuard` (все три switch — таблично, по одной строке на коллекцию × операцию).
3. `GlobalExceptionHandler extends ResponseEntityExceptionHandler` + ветка на
   `DataIntegrityViolationException`; уровень `warn` для 4xx.
4. `enum ErrorCode` и заполнение 110 недостающих текстов; `details` в `ErrorDTO`.
5. `@TransactionalEventListener(AFTER_COMMIT)` + асинхронная рассылка снимков; проверка прав **до**
   регистрации подписки; перечитывание в `REQUIRES_NEW`.
6. `@Version` на сущностях `/api/db`; `expectedUpdatedAt` начинает действовать и для `delete`.
7. Внешние вызовы (LiveKit, S3) вынести за границы транзакций в `startRoomRecording`,
   `syncGamePresence`, `listMine`, `adminList`; задать `hikari.maximum-pool-size` явно.

**Что ломается на фронте:** `public/db.js:194` и `public/app-core.js` уже читают `payload.code` —
добавление `details` совместимо. Смена части статусов (401→403 на смене пароля) требует правки
`public/auth.js:178-195`, иначе останется цикл обновления токена. `@Version` может начать отдавать 409
там, где раньше молча выигрывала последняя запись, — `runTransaction` в `public/db.js:284-319`
это уже умеет (пять повторов), но надо проверить пути без `expectedUpdatedAt`.

### Фаза 2 — контракт без смены маршрутов (1–2 недели)

Цель: типизировать вход и выход, не трогая адреса. Порядок по возрастанию цены —
`/api/token` (1 операция, 7 полей), `/api/tts`, `/api/analytics`, `/api/geo`, `/api/nickname-available`,
`/api/health`, `/api/admin` GET, `/api/monitor`, затем записи и рекордер, затем игра, затем портал.
Для диспетчеров — полиморфное тело: `@JsonTypeInfo(use=NAME, property="action")` + `@JsonSubTypes`,
тогда springdoc рисует `oneOf`, `action` становится enum, а ветки switch получают типизированный аргумент.
Одновременно: базовый уровень Swagger из §5.4, проекции вместо `MAPPER.convertValue(entity, Map.class)`,
`@Schema` на 18 существующих DTO, `therapi`.

**Что ломается на фронте:** при `fail-on-unknown-properties=true` немедленно упадёт `room_name`
в `public/livekit.js:541` и `public/live-preview.js:104` — убрать эти два поля до включения флага.
Формы ответа не меняются, если новые record'ы повторяют текущий набор ключей: несовместимые ветки
(`matchmake`, `record_result`, `sync_room_nickname`, `start`/`finish`) свести к одной записи с
nullable-полями, а не к разным формам — тогда сниффинг по наличию ключа во фронте продолжит работать.
`errorMessage()` (`app-core.js:7860`) переписать на `error.code` в этой же фазе, иначе после заполнения
словаря сервером сломается живая ветка `SABOTAGE_LIMIT_REACHED` (`:12970`).

### Фаза 3 — ресурсные маршруты параллельно старым (2–4 недели)

Новые контроллеры под `/api/v1/**` (§5.1) поднимаются **рядом**; старые диспетчеры остаются как
тонкие адаптеры, делегирующие в те же use-case-сервисы. Порядок: `/api/recordings` (11 действий,
самый дешёвый, заодно админские операции уезжают под существующий `hasRole("ADMIN")`) → `/api/media` →
`/api/admin` → портал по доменам (профиль, друзья, чаты, приглашения, команды, префлайт, матчмейкинг,
рейтинги) → `/api/game` (самый связанный с фронтом) → рекордер (мутирующие режимы из GET в POST).
Одновременно разрезаются жирные сервисы: `GameServiceImpl` → шесть use-case вокруг `GameRules` и
`RoomAccessPolicy`; `SocialService` → `FriendService`/`DirectChatService`/`RoomInviteService`/
`RoomRosterService` (с единственным `joinRoom`); `ProfileService` → `PlayerDirectory`/`ProfileCommandService`/
`PresenceService`/`SabotageEntitlementService`/`UserAdminService`; `TeamService` → `RankedTeamService` +
`PreflightService`; `RecordingService` → пять узких по ролям.

**Что ломается на фронте:** ничего, пока старые адреса живы. Перевод — по одному вызову: в портале это
один хелпер `api(action, data)`, продублированный в `public/portal.js:19`, `public/friends/friends.js:6`,
`public/home/home.js:44`, `public/realtime-social.js:23` и трижды инлайном в `public/portal-shell.js:430,462,472` —
их разумно сначала свести в один клиент, тогда миграция станет правкой в одном файле. `app-core.js` уже
ходит через общий `apiPost` (`:4659, 4708, 7726-7728, 8612, 8709, 12969, 13730, 14618`). Старые адреса
помечаются `@Deprecated` + `deprecated: true` в `@Operation`, снимаются после того, как в логах пропадут
обращения.

### Фаза 4 — уборка (1 неделя)

Единая дверь в базу (`DocumentServiceImpl` через репозиторный слой, `DocumentChangedEvent` из менеджеров —
тогда `/api/game` и `/api/portal` тоже начнут уведомлять подписчиков); удаление ~19 церемониальных
интерфейсов; `MailService` и подключение восстановления пароля; пагинация курсором и конверт
`{items, nextCursor, limit}`; `Cache-Control` по ресурсу вместо сплошного `no-store` + ETag на
состояние рекордера; `allowedOrigins` для WebSocket и токен вне query; `deleteExpiredTokens` в
`ScheduledMaintenance`; реюз-детект refresh-токенов; вынос текстов ботов из констант в ресурсы.

---

## 7. Что осталось непроверенным

1. **Приложение не запускалось.** Нет БД, LiveKit, S3 и переменных окружения; ни один сценарий не
   выполнен вживую. Все выводы получены чтением кода и grep'ом.
2. **Спецификация OpenAPI не снята.** `/v3/api-docs` не запрашивался. Утверждения о том, что именно
   springdoc 2.3.0 отрисует (в частности, привяжет ли он `ErrorDTO` из `@RestControllerAdvice` как
   `default`-ответ через `override-with-generic-response`, и как он покажет
   `@RequestParam Map<String,String>`), — вывод из документированного поведения, а не наблюдение.
3. **Тестов нет, значит нет и регрессионной базы.** Ни одна найденная гонка (потерянное обновление,
   rollback-only, грязные снимки) не воспроизведена нагрузкой — они выведены из кода.
4. **Производительность не измерялась.** N+1 (233 запроса на экран друзей), веер снимков, `avatarDataUrl`
   в опросе рекордера, 20-секундный `block()` на LiveKit — оценки, а не профиль.
5. **Развёртывание неизвестно.** Обратного прокси, WAF и правил CDN в репозитории нет; выводы о том, что
   Swagger и actuator доступны из интернета, опираются на публикацию порта и боевой `URL_BACK`.
6. **Фронтенд просмотрен выборочно.** Проверены `public/db.js`, `public/auth.js`, `public/portal.js`,
   `public/livekit.js`, отдельные места `public/app-core.js` (14 760 строк). Полной карты «какие поля
   каких ответов реально читаются» нет — а именно она нужна, чтобы гарантировать, что новые DTO не потеряют
   ключ. Это первое, что стоит собрать перед фазой 2 (скриптом по вызовам `apiPost`/`api(`).
7. **Числа получены grep'ом** и в паре мест расходятся с исходными оценками находок (кодов ошибок 174,
   вызовов `ApiException.of` 326, `@Transactional` 99, `Map<String,Object>` в интерфейсах 114). Разброс
   ±5% на выводы не влияет, но в отчётности на него полагаться не стоит.
8. **Не анализировались**: SQL-миграции (`db/migration/**`) кроме двух процитированных индексов, модель
   данных на предмет нормализации, `ScheduledMaintenance` целиком, тест-боты как продуктовая фича,
   `service/words`, i18n серверных текстов (серверных фраз в `public/i18n/native/` нет — нерусский игрок
   видит русский текст ошибки).
9. **Не оценивалась стоимость миграции фронта в часах.** Число мест вызова API оценено грубо (~114);
   точный список нужно снять до фазы 3.
