# HOT-HAT — план разделения бекенда на домены

Документ для согласования перед началом работ. Основание — `API-AUDIT.md` (~131 операция за 36 маппингами)
и инвентаризация клиентских записей во фронтенде. Все имена классов, путей и полей — реальные;
ничего сверх найденного в аудите и в коде не добавлено.

Проверено дополнительно к аудиту (grep по `/Users/user/IdeaProjects/hot-hat/public`):
16 транзакционных мест в `app-core.js` (12 `runTransaction` + 4 `writeBatch`), 31 прямая запись
(`setDoc` 12, `updateDoc` 15, `deleteDoc` 4), 4 записи в `livekit.js`/`live-preview.js`,
24 живых подписки `onSnapshot` в шести файлах. Это ~35 логических операций, которых нет в §3 аудита,
потому что они выполняются браузером через `POST /api/db/write`. Итоговый объём работ считается от 131 + 35.

---

## 1. Принцип разбиения

**Граница домена = способность продукта, у которой есть единственный писатель данных.**
Два условия обязательны одновременно:

1. **Единый язык.** Внутри домена слово значит ровно одно. «Сообщение» в личной переписке имеет
   собеседника и признак прочтения; «сообщение» в комнате умирает вместе с комнатой — это два разных
   слова, значит `messaging` и `room` — разные домены. «Присутствие» в портале — «был онлайн N минут
   назад»; «присутствие» в партии — «есть ли трек в LiveKit прямо сейчас» — разные модели, разные
   владельцы, но (см. п. 3) не обязательно разные домены.
2. **Единственный писатель таблицы.** У каждой таблицы ровно один домен, выполняющий INSERT/UPDATE/DELETE.
   Остальные читают через порт владельца и меняют через его же командный порт. Это правило проверяется
   механически (ArchUnit + package-private репозитории), а не дисциплиной.

Из второго условия следует то, что делает план исполнимым: **где нельзя назвать одного писателя строки,
таблица режется**. Сегодня `room` — 103 колонки и 23 jsonb, `app_user` — 36 колонок, `room_player` — 27;
`room.setPhase(...)` зовут пять сервисов (`TeamServiceImpl:320`, `MatchmakingServiceImpl:168,473`,
`AdminServiceImpl:259`, `GameServiceImpl:266,705,1570`, `TestBotsServiceImpl:297,392,407,516`).
Владение «по группам полей» с аннотацией-документацией — это правило, которое держит не компилятор,
а обещание; поэтому в плане заложена миграция схемы (§8, фаза 2), а не аннотация.

### Отвергнутые критерии и причина отказа

| Критерий | Почему отвергнут |
|---|---|
| **По таблицам как они есть сейчас** | Дал бы 8 доменов вокруг `room`/`app_user` — то есть перенёс бы сегодняшнюю свалку в новые пакеты. Именно смешение двух жизненных циклов в одной строке `room` и породило 23 jsonb-поля. |
| **По нынешним файлам сервисов** | `GameServiceImpl` (1624 строки, шесть независимых причин меняться), `SocialServiceImpl` (823) — это следствие RPC-формы, а не структура предметной области. Разрез по ним закрепил бы дефект. |
| **По фазам жизненного цикла партии** (`lobby` → `match` → `after`) | Фаза не является дискриминатором прав и владения: чат комнаты живёт всю партию, зрители входят **только после** старта (`app-core.js:12368`), камеру переключают в игре. Домен «комната до старта» мгновенно превращается в обычный домен «комната» с приклеенным ярлыком. |
| **По актору и уровню прав** (`/me`, хозяин, админ, машина) | Отвечает на вопрос «кто имеет право», а не «что меняется вместе». Даёт `recording`/`recorder` как два домена над одной таблицей и `maintenance`, который не владеет ничем, но пишет в три чужих домена. Уровень актора оставлен как критерий разделения **контроллеров** (см. §3), а не доменов. |
| **По экранам фронтенда** | Экраны — самая изменчивая часть продукта; домен, построенный от экрана, переписывается вместе с вёрсткой. Но потребность реальна (`ensure_profile` зовётся на каждой из семи страниц из `portal-shell.js:472`), поэтому она вынесена в отдельный **read-only слой `bff`** (§2), который ничем не владеет и ничего не пишет. |
| **По частоте со-изменений (change-coupling)** | Метод требует истории правок, а в `hot-hat-back` git-истории нет (`git log` пуст). Критерий, который нечем измерить, — не критерий. Отдельные его выводы (реестр оружия вместо 13 точек правки) взяты в §5. |

### Три правила, которые режут «один контроллер — разные функции по аргументу»

1. **Разные полномочия — разные адреса.** Дискриминатор берётся с сервера, а не из строки клиента.
   Пример: `sync_game_presence` разрезан не по девяти значениям `reason`, а по списку, захардкоженному
   в `GameServiceImpl:630-631` (`livekit-reconnecting`, `livekit-disconnected`, `browser-pagehide`) —
   только эта ветка вправе обнулить `lastSeenAt` и (при полном обрыве) снести комнату.
2. **Разные схемы аутентификации — разные адреса.** `POST /api/monitor` сегодня меняет **форму ответа**
   в зависимости от способа входа (секрет агента против ADMIN) — это два контракта под одним URL.
   То же для `/api/cleanup-*`, `/api/token` (игрок / зритель / делегированный тест-бот).
3. **Строковый переключатель остаётся только там, где он выражает реестр, а не сценарий.**
   Тринадцать видов диверсий проходят один конвейер (фаза → кулдаун → списание → событие) и различаются
   строкой в реестре — это `enum WeaponType` + `Map<WeaponType, WeaponHandler>` внутри домена,
   а не тринадцать эндпоинтов. В контроллере при этом нет ни одного `switch`: тип приезжает enum'ом
   и проверяется Jackson'ом до входа в метод. Обратный пример — `role` в `/api/token`: там за строкой
   стоят три несовместимых набора проверок и три разных способа собрать identity, значит три адреса.

---

## 2. Карта доменов

Порядок миграции — в последнем столбце; обоснование в §8. «Пишет» = единственный владелец на запись
после миграции схемы (фаза 2).

| # | Домен | Пакет | Владеет (таблицы) | Контроллеров | Эндпоинтов | Зависит от (порты) | Очередь |
|---|---|---|---|---|---|---|---|
| 1 | identity | `ru.hothat.identity` | `user_account`, `refresh_token`, `password_reset_token` | 6 | 11 | Mail, Clock | 6 |
| 2 | profile | `ru.hothat.profile` | `player_profile`, `nickname_index`, `legal_consent`, `support_request` | 11 | 18 | identity, ranked-team (чтение) | 6 |
| 3 | friendship | `ru.hothat.friendship` | `friend_link`, `friend_request` | 4 | 9 | profile, ranked-team | 7 |
| 4 | messaging | `ru.hothat.messaging` | `direct_chat`, `direct_chat_message`, `social_inbox` | 5 | 7 | friendship, recording, meme | 7 |
| 5 | ranked-team | `ru.hothat.rankedteam` | `ranked_team`, `ranked_team_name`, `ranked_team_member`, `team_invite`, `ranked_team_preflight` | 9 | 12 | profile, friendship, room (команда лобби) | 7 |
| 6 | matchmaking | `ru.hothat.matchmaking` | `matchmaking_ticket` | 4 | 5 | room, ranked-team, profile | 7 |
| 7 | room | `ru.hothat.room` | `room` (паспорт + `phase`), `room_player`, `room_spectator`, `room_team`, `room_chat_message`, `room_invite` | 15 | 33 | profile, friendship, messaging, match (только команда сброса) | 8 |
| 8 | match | `ru.hothat.match` | `match_state`, `room_word_submission`, `match_appeal_vote` | 10 | 17 | room (`RoomLifecyclePort`, ростер), sabotage, recording, ranking | 9 |
| 9 | sabotage | `ru.hothat.sabotage` | `match_sabotage_state`, `player_arsenal`, `replacement_clip`, `sabotage_game_use` | 6 | 9 | match (часы хода), meme (каталог), room (ростер) | 9 |
| 10 | meme | `ru.hothat.meme` | `meme_library`, объекты `memes/**` | 8 | 12 | ObjectStorage | 4 |
| 11 | recording | `ru.hothat.recording` | `game_recording` (жизненный цикл, библиотека, отметки рекордера) | 9 | 19 | room, match, LiveKit, ObjectStorage | 3 |
| 12 | ranking | `ru.hothat.ranking` | `season_ranking`, `season_ranking_player`, `season_ranking_team`, `ranked_result_event` | 2 | 4 | room, match, ranked-team | 7 |
| 13 | video | `ru.hothat.video` | — (внешняя система за `LiveKitPort`) | 4 | 4 | room, profile | 5 |
| 14 | moderation | `ru.hothat.moderation` | `user_ban`, `moderation_action`, `meme_alert` | 5 | 6 | identity, room, profile | 5 |
| 15 | observability | `ru.hothat.observability` | `analytics_event`, `usage_daily`, `usage_monthly`, `usage_report`, `usage_alert`, `usage_monitor_state`, `usage_mail_counter` | 8 | 9 | room, recording (чтение) | 3 |
| 16 | maintenance | `ru.hothat.maintenance` | `maintenance_run` | 5 | 5 | room, recording, identity, profile (командные порты) | 3 |
| 17 | platform | `ru.hothat.platform` | `feature_flag`, справочник дивизионов | 4 | 4 | Clock | 4 |
| 18 | speech | `ru.hothat.speech` | — (внешний синтезатор за `SpeechPort`) | 1 | 1 | — | 4 |
| 19 | test-bots | `ru.hothat.testbots` | `test_bot_run`, `test_room` | 3 | 4 | match, sabotage, room, video (командные порты) | 10 |
| 20 | realtime | `ru.hothat.realtime` | — (каналы, сессии в памяти) | 8 | 8 (WS) | все домены — только события AFTER_COMMIT | 9 |
| 21 | bff | `ru.hothat.bff` | — (ничего, только чтение) | 6 | 6 | все домены — только read-порты | 3а / 11 |
| — | compat | `ru.hothat.compat` | — (переходный фасад, удаляется) | 4 | 4 | — | 1 → удалить в 12 |
| | **Итого** | | | **137** | **207** (из них 8 WS и 4 переходных) | | |

**Почему `room` не зависит от `match`, а `match` от `room` — зависит.** `phase` принадлежит `room`:
это состояние комнаты, по нему фильтруется лобби (`home/home.js:93`), по нему считаются пороги уборки,
им гейтятся вход зрителя и правка составов. `match` не пишет `phase` напрямую — он просит комнату
перевести жизненный цикл через `RoomLifecyclePort.startMatch()/finishMatch()/returnToSetup()`,
реализованный в `room` и исполняемый **в той же транзакции**. Так снимается цикл `room ↔ match`,
на котором сломалась исходная раскладка.

**Домены без собственных таблиц** (`video`, `speech`, `realtime`, `bff`, отчасти `maintenance`) объявлены
осознанно: за каждым либо внешняя система за портом, либо чистая композиция чтения. Ни один из них
не пишет в чужие таблицы иначе как через командный порт владельца.

---

## 3. Контроллеры

Правило: **класс = ресурс × уровень прав**. Разные HTTP-глаголы одного ресурса под одним уровнем прав
живут в одном классе — компилятор различает маппинги, ветвления по аргументу не возникает.
Разные уровни прав над одним ресурсом — разные классы (админский список записей и свой список записей).
Правило доступа объявляется `@PreAuthorize` на классе; ресурсные предикаты (`@roomAuthz.isHost`) —
тонкие бины над read-портом владельца.

Все фразы ответственности проверены на отсутствие союза «и» и на отсутствие скрытого перечисления.

### 3.1 identity — 6 контроллеров, 11 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права на классе |
|---|---|---|---|---|
| `RegistrationController` | `/api/v1/auth/registrations` | Заводит парольную учётную запись. | 1 | `permitAll` + `@RateLimited(IP)` |
| `SessionController` | `/api/v1/auth/sessions` | Ведёт жизненный цикл сессии. | 4 | `permitAll` (доказательство — сам токен) |
| `GuestSessionController` | `/api/v1/auth/guest-sessions` | Обслуживает сессию без пароля. | 2 | `permitAll` / `hasRole('GUEST')` на upgrade |
| `PasswordResetController` | `/api/v1/auth/password-resets` | Восстанавливает доступ по одноразовой ссылке. | 2 | `permitAll` + `@RateLimited` |
| `MyPasswordController` | `/api/v1/me/account/password` | Меняет пароль по текущему паролю. | 1 | `hasRole('USER')` |
| `MyAccountController` | `/api/v1/me/account` | Отдаёт учётные данные текущей сессии. | 1 | `hasAnyRole('USER','GUEST')` |

### 3.2 profile — 11 контроллеров, 18 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `MyProfileController` | `/api/v1/me/profile` | Ведёт карточку текущего игрока. | 6 | `hasRole('USER')` |
| `OnboardingController` | `/api/v1/me/onboarding` | Завершает первичную настройку одной транзакцией. | 1 | `hasRole('USER')` |
| `MyConsentController` | `/api/v1/me/consents` | Регистрирует принятые правовые согласия. | 1 | `hasAnyRole('USER','GUEST')` |
| `MyPresenceController` | `/api/v1/me/presence` | Отмечает игрока живым в портале. | 1 | `hasRole('USER')` |
| `PresenceSummaryController` | `/api/v1/presence/summary` | Считает игроков, находящихся сейчас в сети. | 1 | `hasRole('USER')` |
| `MyActiveRoomController` | `/api/v1/me/active-room` | Помнит комнату, в которой игрок сейчас находится. | 2 | `hasRole('USER')` |
| `MyDefaultLoadoutController` | `/api/v1/me/meme-loadout` | Хранит стартовый набор мемов игрока. | 2 | `hasRole('USER')` |
| `MySabotageEntitlementController` | `/api/v1/me/sabotage-entitlement` | Отдаёт остаток бесплатных партий с диверсиями. | 1 | `hasRole('USER')` |
| `PublicPlayerController` | `/api/v1/players/{uid}` | Показывает публичную карточку игрока. | 1 | `hasRole('USER')` |
| `NicknameAvailabilityController` | `/api/v1/nicknames/{nickname}/availability` | Сообщает, свободен ли ник. | 1 | `permitAll` + `@RateLimited(IP)` |
| `NicknameRequestController` | `/api/v1/me/nickname-requests` | Принимает заявку на занятый ник. | 1 | `hasRole('USER')` |

### 3.3 friendship — 4 контроллера, 9 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `MyFriendsController` | `/api/v1/me/friends` | Ведёт круг друзей текущего игрока. | 2 | `hasRole('USER')` |
| `FriendRequestController` | `/api/v1/me/friend-requests` | Ведёт заявки в друзья текущего игрока. | 4 | `hasRole('USER')` |
| `FriendRequestAnswerController` | `/api/v1/me/friend-requests/{requestId}` | Разрешает судьбу входящей заявки. | 2 | `@friendRequestAuthz.isRecipient` |
| `FriendPresenceController` | `/api/v1/me/friends/presence` | Отдаёт сетевой статус друзей. | 1 | `hasRole('USER')` |

### 3.4 messaging — 5 контроллеров, 7 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `ChatThreadController` | `/api/v1/me/chats` | Перечисляет личные переписки игрока. | 1 | `hasRole('USER')` |
| `ChatMessageController` | `/api/v1/me/chats/{peerUid}/messages` | Ведёт текстовые сообщения одной переписки. | 2 | `@friendshipAuthz.areFriends` |
| `ChatAttachmentController` | `/api/v1/me/chats/{peerUid}` | Прикладывает вложение к переписке. | 2 | `@friendshipAuthz.areFriends` |
| `ChatReadMarkController` | `/api/v1/me/chats/{peerUid}/read-mark` | Отмечает переписку прочитанной. | 1 | `@friendshipAuthz.areFriends` |
| `SocialInboxController` | `/api/v1/me/social-inbox` | Отдаёт сводку непрочитанного для значков. | 1 | `hasRole('USER')` |

### 3.5 ranked-team — 9 контроллеров, 12 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `MyTeamController` | `/api/v1/me/team` | Отдаёт постоянную команду текущего игрока. | 1 | `@teamAuthz.isMember` |
| `TeamFoundationController` | `/api/v1/teams` | Создаёт постоянную команду с напарником. | 1 | `hasRole('USER')` |
| `PublicTeamController` | `/api/v1/teams/{teamId}` | Показывает публичную карточку команды. | 1 | `hasRole('USER')` |
| `TeamInviteInboxController` | `/api/v1/me/team-invites` | Перечисляет приглашения в команды. | 1 | `hasRole('USER')` |
| `TeamInviteAnswerController` | `/api/v1/me/team-invites/{inviteId}` | Разрешает судьбу приглашения в команду. | 2 | `@teamInviteAuthz.isInvitee` |
| `TeamLobbyController` | `/api/v1/me/team/lobby` | Выдаёт постоянную видеокомнату команды. | 1 | `@teamAuthz.isActiveMember` |
| `PreflightSessionController` | `/api/v1/me/team/preflight` | Ведёт сессию предматчевой проверки. | 3 | `@teamAuthz.isActiveMember` |
| `PreflightMediaCheckController` | `/api/v1/me/team/preflight/media-check` | Принимает отчёт участника о качестве связи. | 1 | `@preflightAuthz.isParticipant` |
| `PreflightReadinessController` | `/api/v1/me/team/preflight/readiness` | Переключает готовность участника к матчу. | 1 | `@preflightAuthz.isParticipant` |

### 3.6 matchmaking — 4 контроллера, 5 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `CasualTicketController` | `/api/v1/matchmaking/casual-tickets` | Подбирает обычную комнату игроку. | 1 | `hasRole('USER')` |
| `RankedTicketController` | `/api/v1/matchmaking/ranked-tickets` | Подбирает рейтинговую комнату команде. | 1 | `@teamAuthz.isCaptain` |
| `MatchTicketController` | `/api/v1/matchmaking/tickets/{roomId}` | Обслуживает собственную заявку на подбор. | 2 | `@ticketAuthz.owns` |
| `RankedRoomEntryController` | `/api/v1/rooms/{roomId}/ranked-entries` | Заводит рейтинговую команду в названную комнату. | 1 | `@teamAuthz.isCaptain` |

### 3.7 room — 15 контроллеров, 33 эндпоинта

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `RoomDirectoryController` | `/api/v1/rooms` | Перечисляет открытые комнаты лобби. | 1 | `hasRole('USER')` |
| `RoomCreationController` | `/api/v1/rooms` | Заводит новую игровую комнату. | 1 | `hasRole('USER')` |
| `RoomController` | `/api/v1/rooms/{roomId}` | Ведёт паспорт комнаты. | 3 | `@roomAuthz.isMember` / `isHost` на запись |
| `RoomResetController` | `/api/v1/rooms/{roomId}/reset` | Возвращает комнату в исходное состояние. | 1 | `@roomAuthz.isHost` |
| `MyRoomSeatController` | `/api/v1/rooms/{roomId}/players/me` | Ведёт собственную карточку игрока в комнате. | 5 | `hasRole('USER')` / `@roomAuthz.isMember` |
| `PlayerEjectionController` | `/api/v1/rooms/{roomId}/players/{uid}` | Удаляет игрока решением хозяина. | 1 | `@roomAuthz.isHost` |
| `MySpectatorSeatController` | `/api/v1/rooms/{roomId}/spectators/me` | Ведёт собственное зрительство в комнате. | 3 | `hasRole('USER')` / `@roomAuthz.isSpectator` |
| `SpectatorPromotionController` | `/api/v1/rooms/{roomId}/spectators/{uid}/promotion` | Переводит зрителя в игроки. | 1 | `@roomAuthz.isHost` |
| `RoomHostController` | `/api/v1/rooms/{roomId}/host` | Держит хозяйское место комнаты. | 3 | `@roomAuthz.isHost` / `isMember` на watch |
| `RoomTeamController` | `/api/v1/rooms/{roomId}/teams` | Ведёт составы команд внутри комнаты. | 5 | `@roomAuthz.isMember` |
| `RoomChatController` | `/api/v1/rooms/{roomId}/chat-messages` | Ведёт чат внутри комнаты. | 4 | `@roomAuthz.isMemberOrSpectator` |
| `RoomInviteIssueController` | `/api/v1/rooms/{roomId}/invites` | Приглашает друга в свою комнату. | 1 | `@roomAuthz.isHost` |
| `RoomInviteController` | `/api/v1/room-invites` | Обслуживает выданные приглашения в комнату. | 2 | сторона приглашения |
| `RoomPublicPresenceController` | `/api/v1/rooms/{roomId}/public-presence` | Публикует счётчик живых игроков для лобби. | 1 | `@roomAuthz.isHost` |
| `RoomRecordingPreferenceController` | `/api/v1/rooms/{roomId}/recording-preference` | Включает запись партий в комнате. | 1 | `hasRole('OWNER')` + `@roomAuthz.isMember` |

### 3.8 match — 10 контроллеров, 17 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `MatchController` | `/api/v1/rooms/{roomId}/matches` | Запускает партию в комнате. | 1 | `@roomAuthz.isHost` |
| `RankedAutostartController` | `/api/v1/rooms/{roomId}/matches/ranked-autostart` | Стартует рейтинговую партию по готовности состава. | 1 | `@roomAuthz.isMember` |
| `MatchStateController` | `/api/v1/rooms/{roomId}/matches/current` | Отдаёт состояние текущей партии. | 1 | `@roomAuthz.isMemberOrSpectator` |
| `WordSubmissionController` | `/api/v1/rooms/{roomId}/word-submissions/me` | Принимает слова игрока для партии. | 1 | `@roomAuthz.isMember` |
| `TurnController` | `/api/v1/rooms/{roomId}/matches/current/turns` | Ведёт ход партии. | 4 | `@matchAuthz.isExplainer` / `isPlayer` |
| `WordVerdictController` | `…/turns/current/words/{wordId}` | Фиксирует исход слова в ходе. | 2 | `@matchAuthz.isExplainer` |
| `MatchPauseController` | `…/matches/current/pause` | Управляет ручной паузой партии. | 2 | `@roomAuthz.isHost` |
| `MatchPresenceController` | `…/matches/current/players/me` | Сверяет присутствие участников партии. | 2 | `@matchAuthz.isPlayer` |
| `AppealController` | `…/matches/current/appeal` | Ведёт апелляцию по спорному слову. | 2 | `@matchAuthz.isIdlePlayer` / `isPlayer` |
| `MatchReturnToSetupController` | `…/matches/current/return-to-setup` | Возвращает доигранную комнату к набору слов. | 1 | `@roomAuthz.isHost` |

### 3.9 sabotage — 6 контроллеров, 9 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `WeaponCatalogController` | `/api/v1/sabotage/weapons` | Отдаёт каталог оружия с его параметрами. | 1 | `hasRole('USER')` |
| `SabotageController` | `…/matches/current/sabotages` | Применяет диверсию против активной команды. | 1 | `@matchAuthz.isIdlePlayer` |
| `MyArsenalController` | `…/matches/current/players/me/arsenal` | Отдаёт боезапас игрока в партии. | 1 | сам игрок |
| `MatchLoadoutController` | `…/matches/current/players/me/loadout` | Ведёт боевой набор мемов на партию. | 2 | `@matchAuthz.isIdlePlayer` |
| `SabotageEntitlementController` | `/api/v1/rooms/{roomId}/games/{gameNumber}/sabotage-entitlement` | Списывает право на партию с диверсиями. | 1 | `@roomAuthz.isMember` |
| `ReplacementClipController` | `…/matches/current/replacement-clips` | Ведёт подменный клип игрока. | 3 | `@matchAuthz.isPlayer` / `@clipAuthz.*` |

### 3.10 meme — 8 контроллеров, 12 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `MemeCatalogController` | `/api/v1/memes` | Ведёт библиотеку мемов. | 3 | `hasRole('USER')` / автор на удаление |
| `MemeTicketController` | `/api/v1/memes/*-tickets` | Выдаёт подписанные ссылки на файлы мемов. | 2 | `hasRole('USER')` + владение путём |
| `MemeFileController` | `/api/v1/media/memes/**` | Перенаправляет на файл мема в хранилище. | 1 | `permitAll` |
| `OrphanMemeFileController` | `/api/v1/memes/orphan-files` | Удаляет незавершённый файл своего мема. | 1 | владелец по префиксу пути |
| `MemeOptimizationController` | `/api/v1/admin/memes/{memeId}/optimized-variant` | Сохраняет пережатую версию мема. | 1 | `hasRole('ADMIN')` |
| `MemeLibraryReconciliationController` | `/api/v1/admin/memes/library-reconciliations` | Сверяет библиотеку мемов с бакетом. | 1 | `hasRole('ADMIN')` |
| `LegacyMemeMigrationController` | `/api/v1/admin/memes/legacy-migrations` | Переносит мемы из старого хранилища. | 2 | `hasRole('OWNER')` |
| `ObjectStorageConfigController` | `/api/v1/admin/object-storage/config` | Показывает настройки объектного хранилища. | 1 | `hasRole('ADMIN')` |

### 3.11 recording — 9 контроллеров, 19 эндпоинтов

| Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|
| `MatchRecordingController` | `/api/v1/rooms/{roomId}/games/{gameNumber}/recording` | Ведёт запись одной партии. | 4 | `@roomAuthz.isMember` |
| `MyRecordingLibraryController` | `/api/v1/me/recordings` | Перечисляет записи, сохранённые игроком. | 1 | `hasRole('USER')` |
| `RecordingRetentionController` | `/api/v1/me/recordings/{recordingId}` | Управляет хранением записи в профиле. | 2 | `@recordingAuthz.isParticipant` |
| `RecordingPlaybackUrlController` | `/api/v1/recordings/{recordingId}/playback-urls` | Выдаёт ссылки на просмотр записи. | 1 | `@recordingAuthz.canWatch` |
| `AdminRecordingController` | `/api/v1/admin/recordings` | Ведёт админский каталог записей. | 3 | `hasRole('ADMIN')` |
| `RecorderSessionController` | `/api/v1/recorder/rooms/{roomId}/games/{gameNumber}/sessions` | Снаряжает рекордер для съёмки партии. | 1 | `hasRole('RECORDER')` |
| `RecorderStateController` | `…/recorder/…/state` | Отдаёт рекордеру состояние сцены. | 3 | `hasRole('RECORDER')` |
| `RecorderProgressController` | `…/recorder/…/progress` | Принимает от рекордера отметки хода съёмки. | 3 | `hasRole('RECORDER')` |
| `EgressWebhookController` | `/api/v1/webhooks/livekit-egress` | Принимает уведомление LiveKit о выгрузке. | 1 | `hasRole('EGRESS')` |

### 3.12 Остальные домены

| Домен | Класс | Базовый путь | Ответственность | Кол-во | Права |
|---|---|---|---|---|---|
| ranking | `RankingBoardController` | `/api/v1/rankings` | Отдаёт сезонные рейтинговые таблицы. | 3 | `hasRole('USER')` |
| ranking | `MatchResultController` | `/api/v1/rooms/{roomId}/games/{gameNumber}/result` | Записывает результат рейтинговой партии. | 1 | участник рейтинговой команды |
| video | `PlayerVideoTokenController` | `…/video-tokens/player` | Выдаёт видеотокен игроку комнаты. | 1 | `@roomAuthz.isMember` |
| video | `SpectatorVideoTokenController` | `…/video-tokens/spectator` | Выдаёт видеотокен зрителю комнаты. | 1 | `@roomAuthz.isSpectator` |
| video | `TestBotVideoTokenController` | `…/video-tokens/test-bot` | Выдаёт видеотокен тестовому боту комнаты. | 1 | `hasRole('ADMIN')` + владелец тестовой комнаты |
| video | `TurnCredentialsController` | `/api/v1/video/turn-credentials` | Выдаёт учётные данные TURN. | 1 | `hasRole('USER')` |
| moderation | `UserBanController` | `/api/v1/admin/bans` | Ведёт баны игроков. | 2 | `hasRole('ADMIN')` |
| moderation | `MyBanStateController` | `/api/v1/me/ban-state` | Сообщает игроку, что он забанен. | 1 | свой uid |
| moderation | `AdminRoomClosureController` | `/api/v1/admin/rooms/{roomId}/closure` | Закрывает комнату решением администратора. | 1 | `hasRole('ADMIN')` |
| moderation | `MemeAlertController` | `/api/v1/admin/meme-alerts/{memeId}` | Снимает жалобу с мема. | 1 | `hasRole('ADMIN')` |
| moderation | `AdminUserDirectoryController` | `/api/v1/admin/users` | Перечисляет зарегистрированных игроков. | 1 | `hasRole('OWNER')` |
| observability | `ServiceHealthController` | `/api/v1/health` | Сообщает, жив ли сервис. | 1 | `permitAll` |
| observability | `ConfigurationReadinessController` | `/api/v1/admin/configuration-readiness` | Показывает, какие интеграции настроены. | 1 | `hasRole('ADMIN')` |
| observability | `AnalyticsEventController` | `/api/v1/analytics/events` | Принимает событие клиентской аналитики. | 1 | `hasAnyRole('USER','GUEST')` |
| observability | `LiveRoomDashboardController` | `/api/v1/admin/dashboard/live-rooms` | Показывает живые комнаты администратору. | 1 | `hasRole('ADMIN')` |
| observability | `UsageStatisticsController` | `/api/v1/admin/dashboard/usage-statistics` | Показывает статистику расхода за период. | 1 | `hasRole('ADMIN')` |
| observability | `AdminUsageSnapshotController` | `/api/v1/admin/usage-snapshots` | Ведёт снимки расхода для администратора. | 2 | `hasRole('ADMIN')` |
| observability | `MachineUsageSnapshotController` | `/api/v1/machine/usage-snapshots` | Снимает расход по расписанию агента. | 1 | `hasRole('MONITOR_AGENT')` |
| observability | `HostMetricsController` | `/api/v1/admin/host-metrics` | Отдаёт метрики хоста. | 1 | `hasRole('ADMIN')` |
| maintenance | `MachineRoomSweepController` | `/api/v1/machine/maintenance/room-sweeps` | Выметает брошенные комнаты по расписанию. | 1 | `hasRole('CRON')` |
| maintenance | `MachineRecordingSweepController` | `/api/v1/machine/maintenance/recording-sweeps` | Удаляет просроченные записи по расписанию. | 1 | `hasRole('CRON')` |
| maintenance | `MachineTokenSweepController` | `/api/v1/machine/maintenance/token-sweeps` | Удаляет истёкшие refresh-токены. | 1 | `hasRole('CRON')` |
| maintenance | `AdminRecordingSweepController` | `/api/v1/admin/maintenance/recording-sweeps` | Удаляет просроченные записи по кнопке администратора. | 1 | `hasRole('ADMIN')` |
| maintenance | `NicknameIndexRepairController` | `/api/v1/admin/maintenance/nickname-index-repairs` | Чинит индекс ников. | 1 | `hasRole('OWNER')` |
| platform | `FeatureFlagController` | `/api/v1/features/{name}` | Отдаёт значение фича-флага. | 1 | `permitAll` |
| platform | `DivisionCatalogController` | `/api/v1/divisions` | Перечисляет языковые дивизионы. | 1 | `permitAll` |
| platform | `DivisionSuggestionController` | `/api/v1/divisions/suggestion` | Подсказывает дивизион по стране запроса. | 1 | `permitAll` |
| platform | `ServerClockController` | `/api/v1/time` | Отдаёт серверное время для калибровки таймеров. | 1 | `hasRole('USER')` |
| speech | `SpeechSynthesisController` | `/api/v1/speech/syntheses` | Синтезирует короткую фразу выбранным голосом. | 1 | `hasRole('ADMIN')` |
| test-bots | `TestBotSquadController` | `/api/v1/test-rooms/{roomId}/bots` | Ведёт отряд тестовых ботов комнаты. | 2 | `hasRole('ADMIN')` + владелец тестовой комнаты |
| test-bots | `TestBotTurnController` | `/api/v1/test-rooms/{roomId}/bots/turns` | Продвигает ход тестовых ботов. | 1 | те же |
| test-bots | `OwnerFartController` | `/api/v1/test-rooms/{roomId}/owner-farts` | Запускает владельческую диверсию в тестовой комнате. | 1 | те же |
| bff | `ShellScreenController` | `/api/v1/screens/shell` | Собирает данные общей шапки портала. | 1 | `hasRole('USER')` |
| bff | `HomeScreenController` | `/api/v1/screens/home` | Собирает стартовый экран лобби. | 1 | `hasRole('USER')` |
| bff | `AccountScreenController` | `/api/v1/screens/account` | Собирает экран профиля. | 1 | `hasRole('USER')` |
| bff | `TeamScreenController` | `/api/v1/screens/team` | Собирает экран постоянной команды. | 1 | `hasRole('USER')` |
| bff | `FriendsScreenController` | `/api/v1/screens/friends` | Собирает экран друзей. | 1 | `hasRole('USER')` |
| bff | `RoomScreenController` | `/api/v1/screens/rooms/{roomId}` | Собирает стартовый снимок игрового экрана. | 1 | `@roomAuthz.isMemberOrSpectator` |

**Экрана рейтингов в `bff` нет намеренно.** Проверка показала, что `viewTabs.onclick` в `portal.js:150`
и так перезапрашивает `ratings` целиком при каждом переключении вкладки, поэтому три доменных GET
(`/rankings/teams`, `/rankings/players`, `/rankings/champion`) дают меньше трафика, а не больше.

---

## 4. Полная таблица эндпоинтов

207 строк: 189 доменных HTTP + 6 экранных (`bff`) + 8 каналов WebSocket + 4 переходных (`compat`).
Столбец «заменяет» указывает либо старое действие, либо строку фронтенда, если операция сегодня
выполняется браузером через `POST /api/db/write`. Прочерк в request-DTO — тела нет.

### 4.1 identity (11)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v1/auth/registrations` | `POST /api/auth/register` | `RegisterAccountRequestDTO` | `IssuedSessionResponseDTO` | 201 | `RegisterAccountUseCase` | permitAll + лимит по IP |
| POST | `/api/v1/auth/sessions` | `POST /api/auth/login` | `OpenSessionRequestDTO` | `IssuedSessionResponseDTO` | 201 | `OpenSessionUseCase` | permitAll + лимит по (email, IP) |
| POST | `/api/v1/auth/sessions/current/renewal` | `POST /api/auth/refresh` | `RenewSessionRequestDTO` | `IssuedSessionResponseDTO` | 200 | `RenewSessionUseCase` | сам refresh-токен; реюз гасит цепочку (A11) |
| DELETE | `/api/v1/auth/sessions/current` | `POST /api/auth/logout` | `RevokeSessionRequestDTO` | — | 204 | `RevokeSessionUseCase` | предъявитель refresh-токена |
| DELETE | `/api/v1/auth/sessions` | `POST /api/auth/logout-all` | — | `RevokedSessionsResponseDTO` | 200 | `RevokeAllSessionsUseCase` | `hasRole('USER')`; поднимает `tokenVersion` |
| POST | `/api/v1/auth/guest-sessions` | `POST /api/auth/guest` | `OpenGuestSessionRequestDTO` | `IssuedGuestSessionResponseDTO` | 201 | `OpenGuestSessionUseCase` | permitAll; токен несёт `ROLE_GUEST` (A7) |
| POST | `/api/v1/auth/guest-sessions/current/upgrade` | `POST /api/auth/link` | `UpgradeGuestAccountRequestDTO` | `IssuedSessionResponseDTO` | 200 | `UpgradeGuestAccountUseCase` | `hasRole('GUEST')`; uid из токена (A4) |
| PUT | `/api/v1/me/account/password` | `POST /api/auth/password` | `ChangeOwnPasswordRequestDTO` | — | 204 | `ChangeOwnPasswordUseCase` | `hasRole('USER')`; неверный пароль → 403 |
| POST | `/api/v1/auth/password-resets` | `POST /api/auth/password-reset` | `RequestPasswordResetRequestDTO` | `PasswordResetAcceptedResponseDTO` | 202 | `RequestPasswordResetUseCase` | permitAll + лимит; письмо через `MailPort` (A3) |
| POST | `/api/v1/auth/password-resets/completions` | `POST /api/auth/password-reset/confirm` | `CompletePasswordResetRequestDTO` | — | 204 | `CompletePasswordResetUseCase` | токен из письма |
| GET | `/api/v1/me/account` | `GET /api/auth/me` (учётная часть) | — | `MyAccountResponseDTO` | 200 | `GetMyAccountUseCase` | `hasAnyRole('USER','GUEST')`; формула админа одна (A5) |

### 4.2 profile (18)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/me/profile` | `portal ensure_profile` (профильная часть) | — | `MyProfileResponseDTO` | 200 | `GetMyProfileUseCase` | свой профиль |
| PUT | `/api/v1/me/profile/nickname` | `portal set_nickname` | `ClaimNicknameRequestDTO` | `ClaimedNicknameResponseDTO` | 200 | `ClaimNicknameUseCase` | свой профиль; 409 `NICKNAME_TAKEN` |
| PUT | `/api/v1/me/profile/display-name` | `PATCH /api/auth/profile`; `app-core.js:900` | `ChangeDisplayNameRequestDTO` | `DisplayNameResponseDTO` | 200 | `ChangeDisplayNameUseCase` | свой профиль |
| PUT | `/api/v1/me/profile/avatar` | `portal set_avatar`; `app-core.js:900` | `ReplaceAvatarRequestDTO` | `StoredAvatarResponseDTO` | 200 | `ReplaceAvatarUseCase` | свой профиль; `@ImageDataUrl(max=120000)` |
| PUT | `/api/v1/me/profile/ui-language` | `portal set_ui_language` | `ChangeUiLanguageRequestDTO` | `UiLanguageResponseDTO` | 200 | `ChangeUiLanguageUseCase` | свой профиль; невалидное → 400 |
| PUT | `/api/v1/me/profile/division` | `portal set_division` | `LockDivisionRequestDTO` | `LockedDivisionResponseDTO` | 200 | `LockDivisionUseCase` | свой профиль; повтор → 409 `DIVISION_LOCKED` |
| POST | `/api/v1/me/onboarding` | цепочка `set_division → set_nickname → record_consent` (`app-core.js:7726-7728`) + `setDoc users/{uid}` (`:7711`) | `CompleteOnboardingRequestDTO` | `CompletedOnboardingResponseDTO` | 200 | `CompleteOnboardingUseCase` | `hasRole('USER')`; 409 если дивизион уже закреплён |
| POST | `/api/v1/me/consents` | `portal record_consent` | `RecordConsentRequestDTO` | `RecordedConsentResponseDTO` | 201 | `RecordConsentUseCase` | authenticated |
| PUT | `/api/v1/me/presence` | `portal presence_ping`; `updateDoc users.lastSeenAt` (`:14704`) | — | `PresenceHeartbeatResponseDTO` | 200 | `TouchMyPresenceUseCase` | свой uid |
| GET | `/api/v1/presence/summary` | `portal presence_summary` | — | `PresenceSummaryResponseDTO` | 200 | `CountOnlinePlayersUseCase` | `hasRole('USER')` |
| PUT | `/api/v1/me/active-room` | `setDoc users/{uid}.activeRoomId` (`:4519`) | `SetActiveRoomRequestDTO` | `ActiveRoomResponseDTO` | 200 | `SetMyActiveRoomUseCase` | свой профиль |
| DELETE | `/api/v1/me/active-room` | `setProfileActiveRoom(null)` (`:4551`) | — | — | 204 | `ClearMyActiveRoomUseCase` | свой профиль |
| GET | `/api/v1/me/meme-loadout` | `portal ensure_profile` (`defaultMemeLoadout`, `loadoutCount`, `loadoutReady`) | — | `DefaultLoadoutResponseDTO` | 200 | `GetDefaultLoadoutUseCase` | свой профиль |
| PUT | `/api/v1/me/meme-loadout` | `setDoc users/{uid}.defaultMemeLoadout` (`:5078`, `:5107`, `:13698`) | `SaveDefaultLoadoutRequestDTO` | `DefaultLoadoutResponseDTO` | 200 | `SaveDefaultLoadoutUseCase` | свой профиль; ровно 5 существующих мемов |
| GET | `/api/v1/me/sabotage-entitlement` | вложенный `sabatage`-блок `ensure_profile` (`ProfileServiceImpl:446-456`), читается `home/home.js` | — | `MySabotageEntitlementResponseDTO` | 200 | `GetMySabotageEntitlementUseCase` | свой uid |
| GET | `/api/v1/players/{uid}` | `portal public_player_profile` | — | `PublicPlayerResponseDTO` | 200 | `GetPublicPlayerUseCase` | `hasRole('USER')`; e-mail не отдаётся |
| GET | `/api/v1/nicknames/{nickname}/availability` | `GET /api/nickname-available` | — | `NicknameAvailabilityResponseDTO` | 200 | `CheckNicknameAvailabilityUseCase` | permitAll + лимит частоты (A10) |
| POST | `/api/v1/me/nickname-requests` | `portal request_nickname` | `SubmitNicknameRequestDTO` | `SubmittedNicknameRequestResponseDTO` | 201 | `SubmitNicknameRequestUseCase` | authenticated; заявка уходит письмом |

### 4.3 friendship (9)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/me/friends` | `portal friends` (массив друзей) | — | `MyFriendsResponseDTO` | 200 | `ListMyFriendsUseCase` | свой uid |
| DELETE | `/api/v1/me/friends/{friendUid}` | `portal remove_friend` | — | — | 204 | `RemoveFriendUseCase` | своя пара; 409 `TEAMMATE_MUST_REMAIN_FRIEND` |
| GET | `/api/v1/me/friends/presence` | `portal friends` (массив `statuses`) | — | `FriendPresenceResponseDTO` | 200 | `ListFriendPresenceUseCase` | свой uid; данные из profile через порт |
| POST | `/api/v1/me/friend-requests/by-nickname` | `portal add_friend` с `nickname` (`friends.js:23`) | `InviteFriendByNicknameRequestDTO` | `SentFriendRequestResponseDTO` | 201 | `InviteFriendByNicknameUseCase` | authenticated; 400 `FRIEND_SELF`, 409 `ALREADY_FRIENDS` |
| POST | `/api/v1/me/friend-requests/by-player` | `portal add_friend` с `friendUid` (`app-core.js:14618`) | `InviteFriendByUidRequestDTO` | `SentFriendRequestResponseDTO` | 201 | `InviteFriendByUidUseCase` | authenticated |
| GET | `/api/v1/me/friend-requests/incoming` | `portal friends` (`incoming`) + WS-подписка (`realtime-social.js:108`) | — | `IncomingFriendRequestsResponseDTO` | 200 | `ListIncomingFriendRequestsUseCase` | свой uid |
| GET | `/api/v1/me/friend-requests/outgoing` | `portal friends` (`outgoing`) + WS-подписка (`:110`) | — | `OutgoingFriendRequestsResponseDTO` | 200 | `ListOutgoingFriendRequestsUseCase` | свой uid |
| POST | `/api/v1/me/friend-requests/{requestId}/acceptance` | `portal answer_friend` с `accept=true` | — | `AcceptedFriendRequestResponseDTO` | 200 | `AcceptFriendRequestUseCase` | получатель заявки |
| POST | `/api/v1/me/friend-requests/{requestId}/rejection` | `portal answer_friend` с `accept=false` | — | — | 204 | `DeclineFriendRequestUseCase` | получатель заявки |

### 4.4 messaging (7)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/me/chats` | `portal social_threads` | — | `ChatThreadsResponseDTO` | 200 | `ListMyChatThreadsUseCase` | свой uid |
| GET | `/api/v1/me/chats/{peerUid}/messages` | `portal get_chat` + подписка `directChats/*/messages` | `ChatHistoryQueryDTO` (limit, cursor) | `ChatHistoryResponseDTO` | 200 | `ReadChatHistoryUseCase` | только друг; 403 `FRIEND_REQUIRED` |
| POST | `/api/v1/me/chats/{peerUid}/messages` | `portal send_chat` без вложения | `SendChatTextRequestDTO` | `SentChatMessageResponseDTO` | 201 | `SendChatTextUseCase` | только друг; обрезка до 800 в политике |
| POST | `/api/v1/me/chats/{peerUid}/photo-messages` | `portal send_chat` с `attachment.kind=image` | `SendChatPhotoRequestDTO` | `SentChatMessageResponseDTO` | 201 | `SendChatPhotoUseCase` | только друг |
| POST | `/api/v1/me/chats/{peerUid}/recording-messages` | `portal send_chat` с `attachment.kind=recording` | `ShareRecordingInChatRequestDTO` | `SentChatMessageResponseDTO` | 201 | `ShareRecordingInChatUseCase` | друг + запись сохранена (403 `RECORDING_NOT_SAVED`) |
| PUT | `/api/v1/me/chats/{peerUid}/read-mark` | `portal mark_chat_read` | — | — | 204 | `MarkChatReadUseCase` | только друг |
| GET | `/api/v1/me/social-inbox` | `GET /api/db/document?path=socialInboxes/{uid}` + подписка (`:111`) | — | `SocialInboxResponseDTO` | 200 | `GetMySocialInboxUseCase` | свой uid |

### 4.5 ranked-team (12)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/me/team` | `portal my_team` (`team`, `partner`, `stats`, `divisionBadge`) | — | `MyTeamResponseDTO` | 200 | `GetMyTeamUseCase` | участник команды |
| POST | `/api/v1/teams` | `portal create_team` | `FoundTeamRequestDTO` | `FoundedTeamResponseDTO` | 201 | `FoundTeamUseCase` | authenticated; 409 `ALREADY_IN_TEAM` |
| GET | `/api/v1/teams/{teamId}` | `portal public_team_profile` | — | `PublicTeamResponseDTO` | 200 | `GetPublicTeamUseCase` | `hasRole('USER')`; рейтинги через `RankingPort` |
| GET | `/api/v1/me/team-invites` | `portal my_team` (массив `invites`, `TeamServiceImpl:133-136`) | — | `TeamInvitesResponseDTO` | 200 | `ListMyTeamInvitesUseCase` | приглашённый |
| POST | `/api/v1/me/team-invites/{inviteId}/acceptance` | `portal accept_team` | — | `AcceptedTeamInviteResponseDTO` | 200 | `AcceptTeamInviteUseCase` | приглашённый |
| POST | `/api/v1/me/team-invites/{inviteId}/rejection` | `portal decline_team` | — | — | 204 | `DeclineTeamInviteUseCase` | приглашённый |
| POST | `/api/v1/me/team/lobby` | `portal ensure_team_lobby` | — | `TeamLobbyResponseDTO` | 200 | `EnsureTeamLobbyUseCase` | участник активной команды; комната через `RoomCommandPort` |
| POST | `/api/v1/me/team/preflight` | `portal team_preflight_start` | `StartPreflightRequestDTO` | `PreflightSessionResponseDTO` | 201 | `StartPreflightUseCase` | капитан; оба лоадаута готовы |
| GET | `/api/v1/me/team/preflight` | `portal team_preflight_status` + подписка (`portal.js:125`) | — | `PreflightSessionResponseDTO` | 200 | `GetPreflightSessionUseCase` | участник команды |
| DELETE | `/api/v1/me/team/preflight` | `portal team_preflight_cancel` | — | — | 204 | `CancelPreflightUseCase` | участник команды |
| PUT | `/api/v1/me/team/preflight/media-check` | `portal team_preflight_media` | `ReportPreflightMediaRequestDTO` | `PreflightMediaResponseDTO` | 200 | `ReportPreflightMediaUseCase` | участник префлайта; TTL 25 с; `teamId` из токена |
| PUT | `/api/v1/me/team/preflight/readiness` | `portal team_preflight_ready` | `SetPreflightReadinessRequestDTO` | `PreflightSessionResponseDTO` | 200 | `SetPreflightReadinessUseCase` | участник; 409 `MEDIA_NOT_READY` |

### 4.6 matchmaking (5)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v1/matchmaking/casual-tickets` | `portal matchmake` с `ranked=false` (`home/home.js:99`) | `OpenCasualTicketRequestDTO` | `MatchTicketResponseDTO` | 201 | `OpenCasualTicketUseCase` | `hasRole('USER')` + готовый мем-набор |
| POST | `/api/v1/matchmaking/ranked-tickets` | `portal matchmake` с `ranked=true` (`portal.js:127`) | `OpenRankedTicketRequestDTO` | `MatchTicketResponseDTO` | 201 | `OpenRankedTicketUseCase` | капитан + пройденный префлайт |
| GET | `/api/v1/matchmaking/tickets/{roomId}` | повторный вызов `matchmake` ради опроса (3-5 с) | — | `MatchTicketResponseDTO` | 200 | `GetMatchTicketUseCase` | владелец заявки |
| DELETE | `/api/v1/matchmaking/tickets/{roomId}` | `portal cancel_matchmake` | — | — | 204 | `CancelMatchTicketUseCase` | владелец заявки (сегодня не проверяется) |
| POST | `/api/v1/rooms/{roomId}/ranked-entries` | `portal ranked_join_room` | `JoinRankedRoomRequestDTO` | `RankedRoomEntryResponseDTO` | 200 | `JoinRankedRoomUseCase` | капитан; режим совпадает с намерением префлайта |

`MatchTicketResponseDTO` — одна форма с полем-перечислением `state = queued | matched | failed`
вместо сегодняшних двух несовместимых ответов (`C6`).

### 4.7 room (33)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/rooms` | подписка `query(rooms, where phase in […])` (`home/home.js:93`) | `RoomDirectoryQueryDTO` | `RoomDirectoryPageResponseDTO` | 200 | `ListOpenRoomsUseCase` | `hasRole('USER')`; приватные и чужие тестовые отсекает сервер |
| POST | `/api/v1/rooms` | `writeBatch` в `createRoom()` (`app-core.js:12101-12120`) | `CreateRoomRequestDTO` | `CreatedRoomResponseDTO` | 201 | `CreateRoomUseCase` | `hasRole('USER')`; `id`/`createdBy` ставит сервер (A1) |
| GET | `/api/v1/rooms/{roomId}` | `GET /api/db/document?path=rooms/{id}` (`:8634`, `:12281`) | — | `RoomSnapshotResponseDTO` | 200 | `GetRoomSnapshotUseCase` | участник или зритель; закрытые поля партии не отдаются |
| PATCH | `/api/v1/rooms/{roomId}` | `updateDoc(roomRef,{name})` (`:12082`), `setTurnDuration` (`:12723`) | `UpdateRoomSettingsRequestDTO` | `RoomSettingsResponseDTO` | 200 | `UpdateRoomSettingsUseCase` | хозяин; фаза `setup` |
| DELETE | `/api/v1/rooms/{roomId}` | `deleteRoomTree` в клиенте (`:13565`) + `POST /api/cleanup-rooms?room_id=` (`:1048`) | — | `RoomClosureResponseDTO` | 200 | `CloseRoomUseCase` | хозяин либо пустая комната по порогам `RoomCleanupPolicy` |
| POST | `/api/v1/rooms/{roomId}/reset` | `resetRoom()` (`:13570`): удаление составов, слов, `phase=setup` | — | `RoomSetupResponseDTO` | 200 | `ResetRoomUseCase` | хозяин; зовёт `MatchStateCommandPort.clear` в своей транзакции |
| PUT | `/api/v1/rooms/{roomId}/players/me` | `setDoc rooms/{id}/players/{uid}` (`:12277`), починка игрока (`:4634`) | `EnterRoomRequestDTO` | `RoomSeatResponseDTO` | 200 | `EnterRoomUseCase` | `hasRole('USER')`; дивизион, приватность, вместимость, бан |
| DELETE | `/api/v1/rooms/{roomId}/players/me` | `deleteDoc(playerRef)` (`:12290`) | — | — | 204 | `LeaveRoomUseCase` | сам игрок |
| PUT | `/api/v1/rooms/{roomId}/players/me/devices` | `updateDoc players{camera,microphone,mediaReadyAt}` (`:5093`, `:12499`, `livekit.js:3175`) | `ReportDeviceStateRequestDTO` | `DeviceStateResponseDTO` | 200 | `ReportDeviceStateUseCase` | сам игрок |
| PUT | `/api/v1/rooms/{roomId}/players/me/nickname` | `portal sync_room_nickname` (`:8612`) | — | `SyncedRoomNicknameResponseDTO` | 200 | `SyncRoomNicknameUseCase` | сам игрок; ник из profile через порт; одна форма ответа |
| PUT | `/api/v1/rooms/{roomId}/players/me/heartbeat` | `updateDoc players.lastSeenAt` (`:8333`, `:14700`) | — | `RoomHeartbeatResponseDTO` | 200 | `TouchRoomSeatUseCase` | сам игрок |
| DELETE | `/api/v1/rooms/{roomId}/players/{uid}` | `game kick_player` (`:9024`) | — | — | 204 | `EjectPlayerUseCase` | хозяин; 409 `KICK_SELF_FORBIDDEN` |
| PUT | `/api/v1/rooms/{roomId}/spectators/me` | `setDoc rooms/{id}/spectators/{uid}` (`:12373`, `live-preview.js:102`) | `TakeSpectatorSeatRequestDTO` | `SpectatorSeatResponseDTO` | 200 | `TakeSpectatorSeatUseCase` | `hasRole('USER')`; приватная комната — 403 |
| DELETE | `/api/v1/rooms/{roomId}/spectators/me` | `deleteDoc spectators/{uid}` (`:4852`, `:12281`, `:12423`) | — | — | 204 | `LeaveSpectatorSeatUseCase` | сам зритель |
| PUT | `/api/v1/rooms/{roomId}/spectators/me/heartbeat` | `updateDoc spectators.lastSeenAt` (`livekit.js:3161`, `live-preview.js:102`) | — | `RoomHeartbeatResponseDTO` | 200 | `TouchSpectatorSeatUseCase` | сам зритель |
| POST | `/api/v1/rooms/{roomId}/spectators/{uid}/promotion` | `portal promote_room_spectator` (`:4837`) | — | `PromotedSpectatorResponseDTO` | 200 | `PromoteSpectatorUseCase` | хозяин; фаза `setup` |
| PUT | `/api/v1/rooms/{roomId}/host` | `game manual_host_transfer` (`:990`) | `TransferHostRequestDTO` | `RoomHostResponseDTO` | 200 | `TransferHostUseCase` | хозяин; цель активна ≤ 5 мин |
| PUT | `/api/v1/rooms/{roomId}/host/activity` | `game setup_host_activity` (`:996`) | `ReportHostActivityRequestDTO` | `HostActivityResponseDTO` | 200 | `ReportHostActivityUseCase` | хозяин; проверка прав раньше проверки фазы; `kind` — enum |
| POST | `/api/v1/rooms/{roomId}/host/handover-watch` | `game setup_host_watch` (`:1016`) | — | `HostHandoverResponseDTO` | 200 | `WatchHostHandoverUseCase` | участник комнаты (A6); 5 форм ответа → одна с `outcome` |
| POST | `/api/v1/rooms/{roomId}/teams` | `runTransaction addTeam()` (`:12626`) | `CreateRoomTeamRequestDTO` | `RoomTeamResponseDTO` | 201 | `CreateRoomTeamUseCase` | игрок комнаты; фаза `setup` |
| DELETE | `/api/v1/rooms/{roomId}/teams/{teamId}` | `runTransaction deleteTeam()` (`:12656`) | — | — | 204 | `DeleteRoomTeamUseCase` | игрок комнаты; фаза `setup`; команда пуста |
| PUT | `/api/v1/rooms/{roomId}/teams/{teamId}/members/me` | `runTransaction joinTeam()` (`:12511`), `updateDoc teamId` (`:12173`, `:12185`) | — | `RoomTeamMembershipResponseDTO` | 200 | `JoinRoomTeamUseCase` | игрок комнаты; фаза `setup` |
| DELETE | `/api/v1/rooms/{roomId}/teams/{teamId}/members/me` | `runTransaction leaveTeam()` (`:12576`) | — | — | 204 | `LeaveRoomTeamUseCase` | сам игрок |
| POST | `/api/v1/rooms/{roomId}/teams/draw` | `game randomize_teams` (`:4869`) | — | `TeamDrawResponseDTO` | 200 | `DrawRoomTeamsUseCase` | игрок комнаты (кнопка намеренно не хозяйская, `:11843`) |
| GET | `/api/v1/rooms/{roomId}/chat-messages` | чтение `rooms/{id}/chat` через `/api/db/query` + подписка (`:8795`) | `RoomChatQueryDTO` | `RoomChatPageResponseDTO` | 200 | `ReadRoomChatUseCase` | участник или зритель |
| POST | `/api/v1/rooms/{roomId}/chat-messages` | `setDoc rooms/{id}/chat/{id}` (`:8478`) | `PostRoomChatMessageRequestDTO` | `RoomChatMessageResponseDTO` | 201 | `PostRoomChatMessageUseCase` | участник или зритель; автор ставит сервер (A1) |
| POST | `/api/v1/rooms/{roomId}/chat-images` | `setDoc` картинки в чат комнаты (`:8473`) | `PostRoomChatImageRequestDTO` | `RoomChatMessageResponseDTO` | 201 | `PostRoomChatImageUseCase` | участник или зритель; `@ImageDataUrl(max=120000)` |
| PATCH | `/api/v1/rooms/{roomId}/chat-messages/{messageId}` | правка сообщения (`:8540`) | `EditRoomChatMessageRequestDTO` | `RoomChatMessageResponseDTO` | 200 | `EditRoomChatMessageUseCase` | только автор |
| POST | `/api/v1/rooms/{roomId}/invites` | `portal send_room_invite` (`:4798`) | `SendRoomInviteRequestDTO` | `SentRoomInviteResponseDTO` | 201 | `SendRoomInviteUseCase` | хозяин + подтверждённая дружба; ветка «уже внутри» — поле ответа |
| GET | `/api/v1/room-invites` | `portal room_invite_statuses` (до 60 id, `realtime-social.js:71-77`) | `RoomInviteStatusQueryDTO` | `RoomInviteStatusesResponseDTO` | 200 | `ReadRoomInviteStatusesUseCase` | сторона приглашения; пакетность сохранена намеренно |
| POST | `/api/v1/room-invites/{inviteId}/acceptance` | `portal accept_room_invite` (`:4818`) | — | `AcceptedRoomInviteResponseDTO` | 200 | `AcceptRoomInviteUseCase` | получатель; 410 `ROOM_INVITE_EXPIRED` |
| PUT | `/api/v1/rooms/{roomId}/public-presence` | `updateDoc {publicActivePlayers, publicPresenceAt}` (`:11931`) | `ReportPublicPresenceRequestDTO` | `PublicPresenceResponseDTO` | 200 | `ReportRoomPublicPresenceUseCase` | хозяин; питает счётчик лобби (`home/home.js:69`) |
| PUT | `/api/v1/rooms/{roomId}/recording-preference` | `game set_recording_preference` (`:12737`) | `SetRecordingPreferenceRequestDTO` | `RecordingPreferenceResponseDTO` | 200 | `SetRecordingPreferenceUseCase` | `hasRole('OWNER')` + игрок комнаты |

### 4.8 match (17)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v1/rooms/{roomId}/matches` | `game prepare_game` (`:12947`) + `runTransaction collectWordsAndStart()` (`:12768`) | `StartMatchRequestDTO` | `MatchStateResponseDTO` | 201 | `StartMatchUseCase` | хозяин + игрок; фаза `setup` или `finished` |
| POST | `/api/v1/rooms/{roomId}/matches/ranked-autostart` | `game ranked_autostart` (`:1035`) | — | `RankedAutostartResponseDTO` | 200 | `AutostartRankedMatchUseCase` | участник рейтинговой комнаты; 3 формы → одна с `outcome` |
| GET | `/api/v1/rooms/{roomId}/matches/current` | игровые поля `rooms/{id}` через `/api/db/document` | — | `MatchStateResponseDTO` | 200 | `GetMatchStateUseCase` | участник или зритель; `currentWord` виден только объясняющему |
| PUT | `/api/v1/rooms/{roomId}/word-submissions/me` | `runTransaction saveWords()` (`:12686`) | `SubmitWordsRequestDTO` | `SubmittedWordsResponseDTO` | 200 | `SubmitWordsUseCase` | игрок комнаты; фаза `setup`; чужие слова не читает никто |
| POST | `…/matches/current/turns` | `runTransaction beginTurn()` (`:12987`) | — | `TurnStateResponseDTO` | 201 | `BeginTurnUseCase` | объясняющий текущей команды; слово тянет сервер |
| POST | `…/turns/current/words/{wordId}/guess` | `writeBatch` + `runTransaction guessed()` (`:13327`, `:13339`) | `GuessWordRequestDTO` | `TurnStateResponseDTO` | 200 | `GuessWordUseCase` | объясняющий; очки начисляет сервер |
| POST | `…/turns/current/words/{wordId}/skip` | `writeBatch` + `runTransaction skipWord()` (`:13404`, `:13409`) | `SkipWordRequestDTO` | `TurnStateResponseDTO` | 200 | `SkipWordUseCase` | объясняющий |
| POST | `…/turns/current/completion` | `runTransaction endTurn()` (`:13456`) | — | `TurnStateResponseDTO` | 200 | `CompleteTurnUseCase` | объясняющий; идемпотентно по `turnId` |
| POST | `…/turns/current/expiry` | `ensureExhaustedTurnAdvanced()` (`:13300`) | — | `TurnStateResponseDTO` | 200 | `ExpireTurnUseCase` | участник партии; время считает `Clock` сервера |
| POST | `…/turns/next` | `runTransaction nextTurn()` (`:13469`) | — | `MatchStateResponseDTO` | 200 | `AdvanceTurnUseCase` | участник партии; порядок команд считает сервер |
| PUT | `…/matches/current/pause` | `game toggle_pause` с `paused=true` (`:12388`) | — | `MatchPauseResponseDTO` | 200 | `PauseMatchUseCase` | хозяин; фаза из `PAUSABLE_PHASES` |
| DELETE | `…/matches/current/pause` | `game toggle_pause` с `paused=false` | — | `MatchPauseResponseDTO` | 200 | `ResumeMatchUseCase` | хозяин |
| PUT | `…/matches/current/players/me/heartbeat` | `game sync_game_presence` со всеми причинами, кроме трёх серверных | — | `MatchPresenceResponseDTO` | 200 | `ReconcileMatchPresenceUseCase` | участник партии; **сверка ростера, пауза и техзавершение выполняются** |
| POST | `…/matches/current/players/me/departure` | `game sync_game_presence` с `livekit-reconnecting`, `livekit-disconnected`, `browser-pagehide` (`GameServiceImpl:630-631`) | `ReportDepartureRequestDTO` | `MatchPresenceResponseDTO` | 200 | `ReportDepartureUseCase` | сам игрок; **только эта ветка обнуляет `lastSeenAt` и сносит комнату** |
| PUT | `…/matches/current/appeal/votes/{wordId}` | `game appeal_vote` (`:9529`) | `CastAppealVoteRequestDTO` | `AppealVotesResponseDTO` | 200 | `CastAppealVoteUseCase` | участник не из отходившей команды; фаза `appeal` |
| POST | `…/matches/current/appeal/closing` | `game finalize_appeal` (`:9537`) | — | `AppealResultResponseDTO` | 200 | `CloseAppealUseCase` | участник партии; идемпотентно по `turnId` |
| POST | `…/matches/current/return-to-setup` | `runTransaction backToSetup()` (`:13508`) | — | `MatchStateResponseDTO` | 200 | `ReturnMatchToSetupUseCase` | хозяин; фаза `finished` |

**Присутствие: почему два адреса, но один алгоритм.** `GameServiceImpl:617-700` — это одна процедура:
опросить ростер LiveKit, вычислить `missing`, поставить или снять паузу, добить партию по
`disconnectLimitMs`. Обе ветки обязаны её выполнять — иначе `pause-timeout-watch` (`app-core.js:11958`,
раз в 10 с при стоящей паузе) перестанет доводить дело до техзавершения. Разница ровно в трёх вещах,
и они настоящие: departure помечает себя офлайн **до** опроса ростера, вычёркивает себя из `connected`
и (только при `livekit-disconnected` и полном обрыве) сносит комнату. Поэтому два контроллера, два
use-case, **один доменный сервис `PresenceReconciliation`**, вызываемый обоими.

### 4.9 sabotage (9)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/sabotage/weapons` | собственная копия списка во фронте (`app-core.js:356 BASE_ARSENAL`) | — | `WeaponCatalogResponseDTO` | 200 | `GetWeaponCatalogUseCase` | `hasRole('USER')` |
| POST | `…/matches/current/sabotages` | `game sabotage` (13 значений `type`) | `UseWeaponRequestDTO` (`type` — `enum WeaponType`) | `SabotageEventResponseDTO` | 201 | `UseWeaponUseCase` | игрок партии не из активной команды; диспетчер — `Map<WeaponType, WeaponHandler>` |
| GET | `…/matches/current/players/me/arsenal` | поле `arsenal` в подписке `players/{uid}` | — | `ArsenalResponseDTO` | 200 | `GetMyArsenalUseCase` | сам игрок |
| PUT | `…/matches/current/players/me/loadout` | `updateDoc {memeLoadout}` при входе в партию (`:12342`) | `SetMatchLoadoutRequestDTO` | `MatchLoadoutResponseDTO` | 200 | `SetMatchLoadoutUseCase` | сам игрок |
| PUT | `…/matches/current/players/me/loadout/slots/{slotIndex}` | `game replace_meme_slot` (`:9683`) | `ReplaceLoadoutSlotRequestDTO` | `MatchLoadoutResponseDTO` | 200 | `ReplaceLoadoutSlotUseCase` | игрок не из активной команды; `slotIndex` 0..4 |
| POST | `/api/v1/rooms/{roomId}/games/{gameNumber}/sabotage-entitlement` | `portal consume_sabotage_game` (`:12969`) | — | `SabotageEntitlementResponseDTO` | 200 | `ConsumeSabotageEntitlementUseCase` | участник партии; 402 `SABOTAGE_LIMIT_REACHED`; идемпотентно по (uid, roomId, gameNumber) |
| POST | `…/matches/current/replacement-clips` | `game replacement_record` (`:3975`) | `OrderReplacementClipRequestDTO` | `ReplacementClipResponseDTO` | 201 | `OrderReplacementClipUseCase` | режим с диверсиями; ≥ 11 с хода; лимит 3 |
| PUT | `…/matches/current/replacement-clips/{clipId}/result` | `game replacement_record_result` (`:3283`) | `ReportClipResultRequestDTO` | `ReplacementClipResponseDTO` | 200 | `ReportClipResultUseCase` | только снятый игрок; фаза сверяется |
| DELETE | `…/matches/current/replacement-clips/{clipId}` | `game replacement_discard` (`:4003`) | — | — | 204 | `DiscardReplacementClipUseCase` | автор клипа; фаза сверяется |

### 4.10 meme (12)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/memes` | `POST /api/db/query` по `memeLibrary` + подписка (`:5969`) | `MemeCatalogQueryDTO` | `MemeCatalogPageResponseDTO` | 200 | `ListMemesUseCase` | `hasRole('USER')`; `mediaPath` резолвит сервер |
| POST | `/api/v1/memes` | `setDoc memeLibrary/{id}` (`:6424`) | `PublishMemeRequestDTO` | `PublishedMemeResponseDTO` | 201 | `PublishMemeUseCase` | `hasRole('USER')`; автора ставит сервер (A1) |
| DELETE | `/api/v1/memes/{memeId}` | `deleteDoc memeLibrary/{id}` | — | — | 204 | `WithdrawMemeUseCase` | автор мема либо ADMIN |
| POST | `/api/v1/memes/upload-tickets` | `media upload_ticket` (`:6368`) | `RequestUploadTicketRequestDTO` | `UploadTicketResponseDTO` | 201 | `IssueUploadTicketUseCase` | `hasRole('USER')`; путь строит сервер |
| POST | `/api/v1/memes/playback-tickets` | `media playback_ticket` (`:5167`) | `RequestPlaybackTicketRequestDTO` | `PlaybackTicketResponseDTO` | 200 | `IssuePlaybackTicketUseCase` | `hasRole('USER')` + владение путём (F15) |
| GET | `/api/v1/media/memes/**` | `GET /api/media?path=` | — | — (302 + `Location`) | 302 | `ResolveMemeFileUseCase` | permitAll; префикс выражен структурой пути |
| DELETE | `/api/v1/memes/orphan-files` | `media delete_own_orphan` (`:6392`) | `DeleteOrphanFileRequestDTO` | — | 204 | `DeleteOrphanFileUseCase` | владелец по префиксу пути |
| PUT | `/api/v1/admin/memes/{memeId}/optimized-variant` | `POST /api/optimize-meme` (`:5739`) | `SaveOptimizedMemeRequestDTO` | `OptimizedMemeResponseDTO` | 200 | `SaveOptimizedMemeUseCase` | `hasRole('ADMIN')` |
| POST | `/api/v1/admin/memes/library-reconciliations` | `POST /api/meme-library-sync` (`meme-s3-only.js:122`) | — | `MemeLibraryReconciliationResponseDTO` | 200 | `ReconcileMemeLibraryUseCase` | `hasRole('ADMIN')` (сегодня — любой вошедший) |
| POST | `/api/v1/admin/memes/legacy-migrations` | `media migrate_legacy_memes` | `MigrateLegacyMemesRequestDTO` | `LegacyMemeMigrationResponseDTO` | 202 | `MigrateLegacyMemesUseCase` | `hasRole('OWNER')`; источники по белому списку хостов |
| POST | `/api/v1/admin/memes/{memeId}/legacy-migration` | `admin migrate_meme_media` | — | `LegacyMemeMigrationResponseDTO` | 202 | `MigrateSingleMemeUseCase` | `hasRole('OWNER')` |
| GET | `/api/v1/admin/object-storage/config` | `media config_status` | — | `ObjectStorageConfigResponseDTO` | 200 | `GetObjectStorageConfigUseCase` | `hasRole('ADMIN')` |

### 4.11 recording (19)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v1/rooms/{roomId}/games/{gameNumber}/recording` | `recordings start` (`:8695`, `:12904`) | `StartRecordingRequestDTO` | `RecordingSessionResponseDTO` | 202 | `StartRecordingUseCase` | игрок комнаты; 3 формы → одна с `state` |
| POST | `…/recording/finish` | `recordings finish` (`:8718`, `:12930`) | — | `RecordingSessionResponseDTO` | 200 | `FinishRecordingUseCase` | участник записи либо игрок; идемпотентно |
| GET | `…/recording` | `recordings status` (`:11552`) | — | `RecordingStatusResponseDTO` | 200 | `GetRecordingStatusUseCase` | игрок комнаты |
| GET | `…/recording/recorder-readiness` | `recordings ready_status` (`:12915`) | — | `RecorderReadinessResponseDTO` | 200 | `GetRecorderReadinessUseCase` | игрок комнаты |
| GET | `/api/v1/me/recordings` | `recordings list_mine` (`portal.js:55`) | `MyRecordingQueryDTO` | `MyRecordingsPageResponseDTO` | 200 | `ListMyRecordingsUseCase` | свой uid; обновление из LiveKit/S3 — фоновой задачей |
| PUT | `/api/v1/me/recordings/{recordingId}` | `recordings save` (`:14731`) | — | `SavedRecordingResponseDTO` | 200 | `SaveRecordingUseCase` | участник записи |
| DELETE | `/api/v1/me/recordings/{recordingId}` | `recordings remove_saved` (`portal.js:68`) | — | — | 204 | `ForgetRecordingUseCase` | владелец (сегодня проверок нет вовсе) |
| GET | `/api/v1/recordings/{recordingId}/playback-urls` | `recordings urls` | — | `RecordingPlaybackUrlsResponseDTO` | 200 | `IssuePlaybackUrlsUseCase` | сохранена вызывающим либо расшарена ему |
| GET | `/api/v1/admin/recordings` | `recordings admin_list` (`admin.js:75`) | `AdminRecordingQueryDTO` | `AdminRecordingsPageResponseDTO` | 200 | `ListRecordingsForAdminUseCase` | `hasRole('ADMIN')` декларативно |
| GET | `/api/v1/admin/recordings/{recordingId}/playback-urls` | `recordings admin_urls` (`admin.js:77`) | — | `AdminRecordingUrlsResponseDTO` | 200 | `IssueAdminPlaybackUrlsUseCase` | `hasRole('ADMIN')` |
| DELETE | `/api/v1/admin/recordings/{recordingId}` | `recordings admin_delete` (`admin.js:367`) | — | — | 204 | `DeleteRecordingByAdminUseCase` | `hasRole('ADMIN')` |
| POST | `/api/v1/recorder/rooms/{roomId}/games/{gameNumber}/sessions` | `GET /api/recording-state?bootstrap=1` (`:13906`) | `OpenRecorderSessionRequestDTO` | `RecorderSessionResponseDTO` | 201 | `OpenRecorderSessionUseCase` | `hasRole('RECORDER')`; подпись в заголовке `X-Hot-Hat-Signature` |
| GET | `…/recorder/…/room-state` | `GET /api/recording-state?state=1` (`:14013`) | — | `RecorderRoomStateResponseDTO` | 200 | `ReadRecorderRoomStateUseCase` | `hasRole('RECORDER')`; `avatarDataUrl` → ссылка |
| GET | `…/recorder/…/live-state` | `GET /api/recording-state` без флагов (`recording-view.js:37`) | — | `RecorderLiveStateResponseDTO` | 200 | `ReadRecorderLiveStateUseCase` | `hasRole('RECORDER')` |
| GET | `…/recorder/…/current-word` | `GET /api/recording-state?word=1` (`:1580`) | — | `RecorderWordResponseDTO` | 200 | `ReadRecorderWordUseCase` | `hasRole('RECORDER')` + включённая запись |
| POST | `…/recorder/…/ready-signal` | `GET /api/recording-state?ready=1` (`:13995`) — мутирующий GET | `SignalRecorderReadyRequestDTO` | `RecorderSignalResponseDTO` | 200 | `SignalRecorderReadyUseCase` | `hasRole('RECORDER')` |
| POST | `…/recorder/…/start-signal` | `GET /api/recording-state?started=1` (`:14051`) — мутирующий GET | `SignalRecorderStartedRequestDTO` | `RecorderSignalResponseDTO` | 200 | `SignalRecorderStartedUseCase` | `hasRole('RECORDER')` |
| POST | `…/recorder/…/ceremony-completion` | `GET /api/recording-state?ceremony_done=1` — мутирующий GET | — | `RecorderCeremonyResponseDTO` | 200 | `CompleteRecorderCeremonyUseCase` | `hasRole('RECORDER')`; 409 `CEREMONY_NOT_READY` |
| POST | `/api/v1/webhooks/livekit-egress` | `POST /api/recording-egress` | `LiveKitEgressWebhookRequestDTO` | `EgressWebhookAckResponseDTO` | 200 | `ApplyEgressWebhookUseCase` | `hasRole('EGRESS')`: наш HMAC + `signing_key` LiveKit |

### 4.12 ranking (4)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/rankings/teams` | `portal ratings` (массив `teams`) | `TeamRankingQueryDTO` | `TeamRankingPageResponseDTO` | 200 | `ListTeamRankingUseCase` | `hasRole('USER')`; дефолты явные |
| GET | `/api/v1/rankings/players` | `portal ratings` (массив `players`) | `PlayerRankingQueryDTO` | `PlayerRankingPageResponseDTO` | 200 | `ListPlayerRankingUseCase` | `hasRole('USER')` |
| GET | `/api/v1/rankings/champion` | `portal ratings` (объект `champion`) | `SeasonChampionQueryDTO` | `SeasonChampionResponseDTO` | 200 | `GetSeasonChampionUseCase` | `hasRole('USER')` |
| POST | `/api/v1/rooms/{roomId}/games/{gameNumber}/result` | `portal record_result` (`:8709`) | `RecordMatchResultRequestDTO` | `RecordedMatchResultResponseDTO` | 201 | `RecordMatchResultUseCase` | участник рейтинговой команды; идемпотентно по (roomId, gameNumber); 3 формы → одна с `outcome` |

### 4.13 video (4)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v1/rooms/{roomId}/video-tokens/player` | `POST /api/token` с `role=player` (`TokenServiceImpl:72-129`) | `IssuePlayerTokenRequestDTO` | `VideoTokenResponseDTO` | 201 | `IssuePlayerTokenUseCase` | строка игрока + правила дивизиона; identity строит сервер |
| POST | `/api/v1/rooms/{roomId}/video-tokens/spectator` | `POST /api/token` с `role=spectator` (+`preview_session`) | `IssueSpectatorTokenRequestDTO` | `VideoTokenResponseDTO` | 201 | `IssueSpectatorTokenUseCase` | строка зрителя; приватная комната — 403 |
| POST | `/api/v1/rooms/{roomId}/video-tokens/test-bot` | `POST /api/token` с чужим `participant_identity` (`test-mode.js:286`) | `IssueTestBotTokenRequestDTO` | `VideoTokenResponseDTO` | 201 | `IssueTestBotTokenUseCase` | `hasRole('ADMIN')` + владелец тестовой комнаты + бот в составе |
| GET | `/api/v1/video/turn-credentials` | блок `turn{}` внутри ответа `POST /api/token` | — | `TurnCredentialsResponseDTO` | 200 | `IssueTurnCredentialsUseCase` | `hasRole('USER')` |

### 4.14 moderation (6)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v1/admin/bans` | `admin ban_user` (`admin.js:359`) | `BanUserRequestDTO` | `BannedUserResponseDTO` | 201 | `BanUserUseCase` | `hasRole('ADMIN')`; поднимает `tokenVersion` через `IdentityCommandPort` |
| DELETE | `/api/v1/admin/bans/{uid}` | `admin unban_user` | — | — | 204 | `UnbanUserUseCase` | `hasRole('ADMIN')`; автор снятия фиксируется |
| GET | `/api/v1/me/ban-state` | `GET /api/db/document?path=bans/{uid}` + подписка (`:113`) | — | `MyBanStateResponseDTO` | 200 | `GetMyBanStateUseCase` | свой uid; закрывает A8 |
| POST | `/api/v1/admin/rooms/{roomId}/closure` | `admin close_room` (`admin.js:353`) | `CloseRoomByAdminRequestDTO` | `AdminRoomClosureResponseDTO` | 200 | `CloseRoomByAdminUseCase` | `hasRole('ADMIN')`; закрытие через `RoomCommandPort` |
| DELETE | `/api/v1/admin/meme-alerts/{memeId}` | `admin delete_meme_alert` (`:5902`) | — | — | 204 | `DismissMemeAlertUseCase` | `hasRole('ADMIN')` |
| GET | `/api/v1/admin/users` | `portal list_users` (`friends.js:15`) | `AdminUserQueryDTO` | `AdminUserPageResponseDTO` | 200 | `ListRegisteredUsersUseCase` | `hasRole('OWNER')` декларативно (A6); страница вместо 5000 |

### 4.15 observability (9)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/health` | `GET /api/health` (без блока `env`) | — | `HealthResponseDTO` | 200 | `CheckHealthUseCase` | permitAll |
| GET | `/api/v1/admin/configuration-readiness` | блок `env` из `GET /api/health` (10 ключей) | — | `ConfigurationReadinessResponseDTO` | 200 | `CheckConfigurationReadinessUseCase` | `hasRole('ADMIN')` (A13) |
| POST | `/api/v1/analytics/events` | `POST /api/analytics` (`:910`) | `RecordAnalyticsEventRequestDTO` | `AnalyticsEventAcceptedResponseDTO` | 202 | `RecordAnalyticsEventUseCase` | authenticated; `eventType` — enum из шести значений |
| GET | `/api/v1/admin/dashboard/live-rooms` | `GET /api/admin?scope=rooms` (`admin.js:302`) | `LiveRoomQueryDTO` | `LiveRoomDashboardResponseDTO` | 200 | `GetLiveRoomDashboardUseCase` | `hasRole('ADMIN')` |
| GET | `/api/v1/admin/dashboard/usage-statistics` | `GET /api/admin?scope=stats` (`admin.js:89`) | `UsageStatisticsQueryDTO` | `UsageStatisticsResponseDTO` | 200 | `GetUsageStatisticsUseCase` | `hasRole('ADMIN')`; даты разбирает `DateRange` |
| GET | `/api/v1/admin/usage-snapshots` | `GET /api/monitor` (`admin.js:240`) | `UsageSnapshotQueryDTO` | `UsageSnapshotPageResponseDTO` | 200 | `ListUsageSnapshotsUseCase` | `hasRole('ADMIN')` матчером (A12) |
| POST | `/api/v1/admin/usage-snapshots` | `POST /api/monitor` от администратора (`admin.js:237`) | — | `AdminUsageSnapshotResponseDTO` | 201 | `TakeUsageSnapshotByAdminUseCase` | `hasRole('ADMIN')` |
| POST | `/api/v1/machine/usage-snapshots` | `POST /api/monitor` с `X-Hot-Hat-Monitor-Secret` | `IngestUsageCounterRequestDTO` | `MachineUsageSnapshotResponseDTO` | 201 | `TakeUsageSnapshotByAgentUseCase` | `hasRole('MONITOR_AGENT')`; сравнение `MessageDigest.isEqual` |
| GET | `/api/v1/admin/host-metrics` | `HostMetricsService` внутри снимка монитора | — | `HostMetricsResponseDTO` | 200 | `GetHostMetricsUseCase` | `hasRole('ADMIN')` |

### 4.16 maintenance (5)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| POST | `/api/v1/machine/maintenance/room-sweeps` | `GET\|POST /api/cleanup-rooms?sweep=1`; `portal cleanup_rooms` удаляется (A2) | — | `RoomSweepResponseDTO` | 200 | `SweepAbandonedRoomsUseCase` | `hasRole('CRON')`; удаление через `RoomCommandPort` |
| POST | `/api/v1/machine/maintenance/recording-sweeps` | `GET\|POST /api/cleanup-recordings` | — | `RecordingSweepResponseDTO` | 200 | `SweepExpiredRecordingsUseCase` | `hasRole('CRON')` |
| POST | `/api/v1/machine/maintenance/token-sweeps` | новый: `SaverUser.deleteExpiredTokens` реализован, не вызывается (A11) | — | `TokenSweepResponseDTO` | 200 | `SweepExpiredTokensUseCase` | `hasRole('CRON')` |
| POST | `/api/v1/admin/maintenance/recording-sweeps` | `/api/cleanup-recordings` от ADMIN | — | `RecordingSweepResponseDTO` | 200 | `SweepExpiredRecordingsUseCase` | `hasRole('ADMIN')` — тот же use-case, другой актор |
| POST | `/api/v1/admin/maintenance/nickname-index-repairs` | `portal repair_nickname_indexes` = `migrate_nicknames` (один метод) | — | `NicknameIndexRepairResponseDTO` | 202 | `RepairNicknameIndexUseCase` | `hasRole('OWNER')` |

### 4.17 platform (4), speech (1), test-bots (4)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/features/{name}` | `GET /api/features/{name}` (`features.js:54`) | — | `FeatureFlagResponseDTO` | 200 | `ReadFeatureFlagUseCase` | permitAll; имя из закрытого набора |
| GET | `/api/v1/divisions` | список из девяти языков, зашитый в трёх местах | — | `DivisionCatalogResponseDTO` | 200 | `ListDivisionsUseCase` | permitAll |
| GET | `/api/v1/divisions/suggestion` | `GET /api/geo` (`:834`) | `DivisionSuggestionQueryDTO` (заголовки) | `DivisionSuggestionResponseDTO` | 200 | `SuggestDivisionUseCase` | permitAll |
| GET | `/api/v1/time` | калибровка через `updateDoc(clockProbeAt)` + `getDocFromServer` (`:8053-8062`) | — | `ServerTimeResponseDTO` | 200 | `ReadServerTimeUseCase` | `hasRole('USER')`; убирает запись в БД ради чтения часов |
| POST | `/api/v1/speech/syntheses` | `POST /api/tts` (`:2041`) | `SynthesizeSpeechRequestDTO` | — (тело `audio/mpeg`) | 201 | `SynthesizeSpeechUseCase` | `hasRole('ADMIN')`; алиас `voice_id` через `@JsonAlias` |
| POST | `/api/v1/test-rooms/{roomId}/bots` | `test-bots setup` (`:12123`) | `SetUpTestBotsRequestDTO` | `TestBotSquadResponseDTO` | 201 | `SetUpTestBotsUseCase` | `hasRole('ADMIN')` + фича `bot_enabled` + владелец |
| DELETE | `/api/v1/test-rooms/{roomId}/bots` | `test-bots stop` (`:12418`) | — | — | 204 | `StopTestBotsUseCase` | те же |
| POST | `/api/v1/test-rooms/{roomId}/bots/turns` | `test-bots tick` (`test-mode.js:351`) | — | `TestBotTurnResponseDTO` | 200 | `AdvanceTestBotTurnUseCase` | те же; 11 условных ключей → одна схема с nullable |
| POST | `/api/v1/test-rooms/{roomId}/owner-farts` | `test-bots fart` (`:2281`) | `FireOwnerFartRequestDTO` | `FiredOwnerFartResponseDTO` | 201 | `FireOwnerFartUseCase` | те же; идемпотентно по `event_id` |

### 4.18 realtime — каналы WebSocket (8)

| Событие | Путь | Заменяет | Hello DTO | Event DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| CONNECT | `/ws/v1/rooms/{roomId}` | шесть подписок `/ws/documents` (`:8634`, `:8750`, `:8761`, `:8788`, `:8795`, `:8806`) | `RoomChannelHelloDTO` | `RoomChannelEventDTO` | 101 | `StreamRoomChannelUseCase` | участник или зритель; токен в `Sec-WebSocket-Protocol`; перепроверка `tokenVersion` раз в 60 с (A9) |
| CONNECT | `/ws/v1/lobby` | подписка `query(rooms, where phase in […])` (`home/home.js:93`) | `LobbyChannelHelloDTO` | `LobbyChannelEventDTO` | 101 | `StreamLobbyChannelUseCase` | `hasRole('USER')`; приватные не рассылаются |
| CONNECT | `/ws/v1/lobby/rooms/{roomId}` | три подписки состава выбранной комнаты (`home/home.js:88`) | `RoomPreviewHelloDTO` | `RoomPreviewEventDTO` | 101 | `StreamRoomPreviewUseCase` | `hasRole('USER')`; только публичные поля |
| CONNECT | `/ws/v1/me/social` | четыре подписки (`realtime-social.js:108,110,111,113`) | `SocialChannelHelloDTO` | `SocialChannelEventDTO` | 101 | `StreamSocialChannelUseCase` | свой uid; событие бана закрывает соединение |
| CONNECT | `/ws/v1/me/chats/{peerUid}` | подписка `directChats/{pair}/messages` (`:99`, `portal.js:121`, `friends.js:20`) | `DirectChatHelloDTO` | `DirectChatEventDTO` | 101 | `StreamDirectChatUseCase` | только друг; `pair` вычисляет сервер |
| CONNECT | `/ws/v1/teams/{teamId}/preflight` | подписка `rankedTeamPreflights/{teamId}` (`portal.js:125`) | `PreflightHelloDTO` | `PreflightEventDTO` | 101 | `StreamPreflightUseCase` | участник команды; расчёт готовности — на сервере |
| CONNECT | `/ws/v1/meme-library` | подписка `collection(memeLibrary)` (`:5969`) | `MemeLibraryHelloDTO` | `MemeLibraryEventDTO` | 101 | `StreamMemeLibraryUseCase` | `hasRole('USER')` |
| PING | `/ws/v1/**` | `{type:"ping"}` → `{type:"pong"}` | `ChannelPingFrameDTO` | `ChannelPongFrameDTO` | — | `HandleChannelHeartbeatUseCase` | открытая сессия |

Подписка на `rooms/{id}/players/{uid}` из `live-preview.js:34` и `bans/{uid}` из `live-preview.js:99`
обслуживаются каналами `/ws/v1/lobby/rooms/{roomId}` и `/ws/v1/me/social` соответственно.

### 4.19 bff — экранные сборки, только чтение (6)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/screens/shell` | `ensure_profile` + `friends` на каждой из семи страниц (`portal-shell.js:430,472`) | — | `ShellScreenResponseDTO` | 200 | `ComposeShellScreenUseCase` | `hasRole('USER')` |
| GET | `/api/v1/screens/home` | `ensure_profile` + `presence_summary` + первый снимок списка комнат | — | `HomeScreenResponseDTO` | 200 | `ComposeHomeScreenUseCase` | `hasRole('USER')` |
| GET | `/api/v1/screens/account` | `ensure_profile` + `my_team` + `list_mine` (`portal.js:105`) | — | `AccountScreenResponseDTO` | 200 | `ComposeAccountScreenUseCase` | `hasRole('USER')` |
| GET | `/api/v1/screens/team` | `my_team` + `friends` + `team_preflight_status` (`portal.js:106-123`) | — | `TeamScreenResponseDTO` | 200 | `ComposeTeamScreenUseCase` | `hasRole('USER')` |
| GET | `/api/v1/screens/friends` | `friends` + `my_team` + `social_threads` (`friends.js:14-15`) | — | `FriendsScreenResponseDTO` | 200 | `ComposeFriendsScreenUseCase` | `hasRole('USER')` |
| GET | `/api/v1/screens/rooms/{roomId}` | первые снимки шести подписок комнаты + `ensure_profile` + `friends` (`:4706`) | — | `RoomScreenResponseDTO` | 200 | `ComposeRoomScreenUseCase` | участник или зритель |

### 4.20 compat — переходный слой, удаляется (4)

| Метод | Путь | Заменяет | Request DTO | Response DTO | Код | Use-case | Права |
|---|---|---|---|---|---|---|---|
| GET | `/api/db/document` | сам себя | `LegacyDocumentQueryDTO` | `DocumentSnapshotResponseDTO` | 200 | `ReadLegacyDocumentUseCase` | прежний `checkRead`; `@Deprecated` + `Sunset` |
| POST | `/api/db/query` | сам себя | `LegacyQueryRequestDTO` | `LegacyQueryPageResponseDTO` | 200 | `RunLegacyQueryUseCase` | прежний `checkQuery` + каскадный `@Valid` (A8) |
| POST | `/api/db/write` | 16 клиентских транзакций и 31 прямую запись | — (тело не читается) | `ErrorDTO(code=USE_DOMAIN_COMMAND, details.route)` | 410 | — | закрывается по коллекциям (см. §8) |
| CONNECT | `/ws/documents` | сам себя | `SubscribeMessageDTO` | `SnapshotMessageDTO` | 101 | `LegacySubscribeUseCase` | прежний `checkRead`; удаляется вместе с чтением |

### 4.21 Что удалено намеренно и не имеет замены

| Что | Почему |
|---|---|
| `portal cleanup_rooms` | Дубль `/api/cleanup-rooms` без единой проверки прав; `MatchmakingService.cleanup()` не принимает пользователя; удаляет managed-комнату до записи MMR (A2). Фронт не зовёт его ни разу. |
| `GET /api/admin?scope=all` | Не операция, а склейка двух ответов. `admin.js` уже умеет запрашивать `rooms` и `stats` порознь (`:89`, `:302`). |
| `GET`-варианты `/api/cleanup-*` | Мутация на GET. Остаётся только POST. |
| `preview_session` в `/api/token` | Не параметр, а сценарий: identity `preview-{uid}-{session}` строит сервер внутри `IssueSpectatorTokenUseCase`. |
| Блок `env` в публичном `/api/health` | Уезжает под ADMIN в `configuration-readiness` (A13). |
| `room_name` в `/api/token` | Сервер не читает его никогда (C15); при `fail-on-unknown-properties=true` станет 400 — поле убирается из `livekit.js:541` и `live-preview.js:104` заранее. |
| `expectedUpdatedAt` | Исчезает вместе с `/api/db/write`; конкурентность становится делом `@Version` внутри use-case. |

---

## 5. Слои и правила

### 5.1 Структура пакета домена

```
ru.hothat.<домен>
  api/          контроллеры + DTO запроса и ответа + мапперы DTO↔доменные value-объекты
  usecase/      по классу на сценарий; держит транзакцию и порядок шагов
  domain/       чистые правила без Spring и без БД (политики, реестры, value-объекты)
  port/         интерфейсы, которые домен ТРЕБУЕТ от соседей и от внешних систем
  spi/          интерфейсы, которые домен ПРЕДОСТАВЛЯЕТ соседям (+ реализация здесь же)
  store/        репозитории и JPA-сущности; классы package-private
```

Наружу видны только `api`-DTO и интерфейсы из `spi`. `store` и `domain` — package-private.

### 5.2 Контракт слоёв

| Слой | Обязан | Запрещено |
|---|---|---|
| **Controller** | Разобрать DTO, вызвать **один** use-case, вернуть `ResponseEntity<ResponseDTO>` | Любое доменное решение; сборка `Map`; `try/catch`; разбор дат (сегодня `AdminController:48-76`); обращение к репозиторию (сегодня `PublicController`); проверка прав руками; вызов второго use-case; `switch` по полю тела |
| **UseCase** | Один сценарий — один публичный метод; `@PreAuthorize` на методе; `@Transactional` здесь и только здесь; порядок шагов | Внешний вызов (LiveKit, S3, почта, TTS) внутри транзакции; вызов чужого use-case; `Map<String,Object>` в сигнатуре; возврат JPA-сущности |
| **Domain** | Чистые правила: `RoomAccessPolicy`, `TurnRules`, `WeaponRegistry`, `FriendshipPolicy`, `NicknamePolicy`, `RoomCleanupPolicy`, `DateRange`, `Seasons` | Spring-аннотации; обращение к БД; `System.currentTimeMillis()` (только `Clock`); `new Random()` (только `RandomSource`) |
| **Store** | Единственная дверь в базу; `@Version` на конкурентных сущностях | Быть публичным для другого домена; `EntityManager` в обход менеджера (сегодня так делает `DocumentServiceImpl:44`) |

Церемониальные интерфейсы, у которых один impl и все методы `Map` (~19 штук в портале, игре, записях,
админке, мониторинге), удаляются. Остаются те, за которыми стоит внешняя система или package-private
реализация.

### 5.3 Как домены общаются

Ровно три способа, других нет.

1. **Read-порт** — синхронное чтение чужих данных. Интерфейс объявлен в `spi` владельца, возвращает
   маленький `record`, не сущность и не `Map`. Порты этой раскладки:
   `RoomMembershipPort` (кто хозяин, кто игрок, кто зритель, какая фаза), `RoomStatePort` (паспорт
   комнаты для `match`, `recording`, `video`, `ranking`), `MatchClockPort` (часы хода для `sabotage`),
   `ProfileDirectoryPort` (ник, аватар, дивизион), `FriendshipPort` (`areFriends`),
   `TeamMembershipPort` (капитан, партнёр, статус), `PreflightReadinessPort`,
   `RecordingOwnershipPort` (`isSavedBy`), `MemeCatalogPort` (существование мема, длительность),
   `SabotageEntitlementPort` (остаток бесплатных партий), `RankingPort` (позиция команды).
2. **Командный порт** — синхронное изменение чужих данных **внутри текущей транзакции**.
   Разрешён только там, где инвариант не выдерживает разрыва (список ниже). Реализация принадлежит
   владельцу таблицы, поэтому «единственный писатель» не нарушается: писать продолжает владелец,
   вызывающий лишь просит.
3. **Доменное событие** — `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`.
   Только для того, что вправе опоздать: проекции, счётчики инбокса, аналитика, рассылка снимков в
   `realtime`, письма. **Инварианты через события не выражаются никогда.**

**Прямо запрещено:** вызывать чужой use-case; инжектить чужой репозиторий; принимать или возвращать
чужую JPA-сущность; открывать транзакцию поверх двух доменов иначе как через командный порт;
обращаться к чужой таблице по имени. Проверяется ArchUnit-правилами
«`ru.hothat.X` не зависит от `ru.hothat.Y..store`», «`ru.hothat.X` не зависит от `ru.hothat.Y..usecase`»,
«циклов между доменами нет», «в `ru.hothat.bff` нет `@Transactional(readOnly=false)` и ссылок на `store`».

### 5.4 Транзакции, пересекающие границы доменов

Эти восемь операций сегодня атомарны и обязаны остаться атомарными. Для каждой указан командный порт;
разрыв на события здесь запрещён явно.

| Операция | Что пишет | Механизм |
|---|---|---|
| Создание комнаты (`app-core.js:12101`) | `room` + первая строка игрока + стартовый набор мемов | `CreateRoomUseCase` (room) → `LoadoutCommandPort` (sabotage) |
| Вход в комнату (`:12277`, `:4634`) | `room_player` + арсенал + `memeLoadout` | `EnterRoomUseCase` (room) → `LoadoutCommandPort` |
| Старт партии (`GameServiceImpl.prepareGame:85-151`) | `match_state` + фаза комнаты + арсеналы всех игроков + удаление зрителей | `StartMatchUseCase` (match) → `RoomLifecyclePort` + `ArsenalCommandPort` |
| Угадано / пропущено (`:13327`, `:13404`) | `match_state` + очки команды | внутри `match` после миграции схемы: `room_team.score` переезжает в `match_state.scores` |
| Закрытие апелляции (`GameServiceImpl:1425-1530`) | очки + арсеналы + выдача мемов + фаза | `CloseAppealUseCase` (match) → `ArsenalCommandPort` + `RoomLifecyclePort` |
| Уход игрока (`GameServiceImpl:617-700`) | пауза, техзавершение, остановка записи, снос комнаты | `ReportDepartureUseCase` (match) → `RecordingCommandPort` + `RoomCommandPort` |
| Приглашение в комнату (`SocialServiceImpl:501-580`) | `room_invite` + сообщение в переписке + инбокс | `SendRoomInviteUseCase` (room) → `MessagingCommandPort` |
| Лобби команды (`TeamServiceImpl:309-352`) | комната, состав, строка игрока | `EnsureTeamLobbyUseCase` (ranked-team) → `RoomCommandPort` |
| Бан игрока (`AdminServiceImpl:278-330`) | `user_ban` + `tokenVersion` + отзыв refresh + `room_player.bannedAt` | `BanUserUseCase` (moderation) → `IdentityCommandPort` + `RoomCommandPort` |

### 5.5 Где живёт авторизация

- `@EnableMethodSecurity`; `@PreAuthorize` на **публичных методах use-case**, а не в контроллерах.
  На классе контроллера — та же аннотация как декларация намерения для springdoc и для ревью.
- Роли: `ROLE_USER`, `ROLE_GUEST` (новая, закрывает A7), `ROLE_ADMIN`,
  `ROLE_OWNER` (сегодня выдаётся и не проверяется нигде — получает трёх потребителей:
  список учёток, миграции мемов, починка индекса ников), и четыре машинные —
  `ROLE_RECORDER` (`RecorderSignatureFilter`, HMAC из заголовка), `ROLE_EGRESS` (`EgressWebhookFilter`,
  наш HMAC + `signing_key` LiveKit), `ROLE_CRON` (`CronSecretFilter`), `ROLE_MONITOR_AGENT`
  (`MonitorSecretFilter`). Все секреты сравниваются `MessageDigest.isEqual`.
- Ресурсные предикаты — бины `@roomAuthz`, `@matchAuthz`, `@teamAuthz`, `@preflightAuthz`,
  `@friendshipAuthz`, `@recordingAuthz`, `@clipAuthz`, `@memeAuthz`, `@ticketAuthz` в
  `ru.hothat.security.authz`; каждый — тонкая обёртка над read-портом владельца с `@RequestScope`-кешем,
  чтобы SpEL не устраивал второе чтение комнаты на запрос. Бизнес-правил внутри нет.
- `HotHatUser` производит только `PrincipalResolver`. Ни один домен не пересчитывает `admin`/`owner`
  заново — это устраняет две несовпадающие формулы админа (A5) и семь точек ручной проверки.
- Матчеры в `WebSecurityConfig` остаются как второй рубеж (`/api/v1/admin/**`, `/api/v1/machine/**`),
  но правило по умолчанию для остальных — `.anyRequest().authenticated()`. Полагаться только на
  `denyAll()` + метод-секьюрити нельзя: `AuthorizationFilter` отрабатывает раньше DispatcherServlet
  и отсечёт запрос до того, как аннотация будет прочитана.

### 5.6 Куда девается `GameRules`

`service/game/GameRules.java` (274 строки, 12 публичных методов) — единственный кусок чистых правил в
проекте, и он сохраняется, но перестаёт быть общей статикой над JPA-сущностями. Разрезается по владельцам:

| Что сейчас в `GameRules` | Куда переезжает |
|---|---|
| `BASE_ARSENAL`, `ARSENAL_KEYS`, `COOLDOWN_MS`, `arsenal()`, `rewardForScore()`, `specialRewardsBetween()`, `sabotageLocks()`, `appendRecentSabotage()` | `ru.hothat.sabotage.domain.WeaponRegistry` — единый реестр `record Weapon(WeaponType type, String ammoKey, int baseAmmo, long durationMs, boolean lock, boolean advanced, int reward)`. `BASE_ARSENAL` и `ARSENAL_KEYS` **выводятся** из реестра, поэтому их расхождение (сегодня даёт NPE) становится невыразимым. Каталог отдаётся наружу через `GET /api/v1/sabotage/weapons`, и фронт выбрасывает свою копию (`app-core.js:356`) |
| `MEME_LOADOUT_SIZE`, `BUILTIN_MEMES`, `normalizedLoadout()`, `MemeQueue`, `memeQueue()`, `grantMemes()`, `applyQueue()` | `ru.hothat.sabotage.domain.LoadoutRules` |
| `PAUSABLE_PHASES`, `currentTurnDeadline()`, `turnDurationSeconds()` | `ru.hothat.match.domain.TurnRules` |
| `rosterForTeam()`, `allGamePlayers()` | `ru.hothat.room.domain.RosterRules`, доступ для `match` — через `RoomMembershipPort` |
| `testBotIds()` | `ru.hothat.testbots.domain.BotRoster` |
| `Divisions`, список из девяти языков (сегодня продублирован в `Divisions`, `DocumentAccessGuard.DIVISIONS` и во фронте) | `ru.hothat.platform.domain.DivisionCatalog`, отдаётся через `GET /api/v1/divisions` |

Все переехавшие правила принимают value-объекты, а не `Room`/`RoomPlayer`, — тогда «правило знает про
сущность соседа» перестаёт компилироваться.

---

## 6. Судьба `/api/db` и WebSocket

**Решение асимметричное: запись удаляется, чтение и подписки переезжают на типизированные каналы,
шлюз доживает один релиз как read-only фасад и умирает.**

### 6.1 Почему запись обязана исчезнуть

Право на запись зависит от пути, который приезжает строкой в теле, поэтому его нельзя объявить ни на
классе, ни на методе — оно навсегда остаётся ручным `switch` внутри `DocumentAccessGuard`. Проверено:
`DocumentAccessGuard.java:119-121` — ветка `case "rooms", "rooms/*/teams", "rooms/*/wordSubmissions",
"rooms/*/chat", "memeLibrary" -> { }` пуста. Любой вошедший переписывает `createdBy` чужой комнаты и
становится её хозяином (A1). Это не баг конфигурации: пока движок партии живёт в браузере, правило
«кто вправе менять ход» негде проверить. 16 транзакций и 31 запись из `app-core.js` — это и есть
недостающая серверная логика, а не транспорт.

### 6.2 Почему чтение переезжает не сразу и не одним куском

Фронт держит 24 живых подписки в шести файлах. Универсальный `path`-шлюз не умеет резать поля по роли:
`PUBLIC_READS` (`DocumentAccessGuard.java:54-56`) включает `rooms/*/wordSubmissions`, то есть слова чужой
команды сегодня читаются штатным запросом. Поэтому чтение переезжает на именованные каналы (§4.18),
где правило доступа принадлежит домену-владельцу, а не транспорту. Но переезд идёт после того, как
записи станут серверными, — иначе пришлось бы одновременно переписывать и запись, и всю модель состояния.

### 6.3 Механика перехода (три обязательных элемента)

1. **Мост `DocumentChangedEvent`.** Проверено: событие публикуется ровно из одного места —
   `DocumentServiceImpl.java:132`, внутри `write()`; единственный слушатель — `DocumentSocketHandler:104`.
   Значит первая же запись, ушедшая на доменный use-case, перестанет доходить до подписчиков **молча**.
   Поэтому каждый новый пишущий use-case на время перехода публикует `DocumentChangedEvent` для
   затронутой коллекции — через `AFTER_COMMIT`, а не внутри транзакции (это заодно чинит B1).
   Мост снимается вместе с `/ws/documents`.
2. **Закрытие дыры A1 по коллекциям, а не одним прыжком.** `checkWrite` — это `switch` по коллекциям,
   поэтому каждая мигрировавшая коллекция немедленно переводится в `SERVER_ONLY_WRITES`:
   `memeLibrary` → после `POST /api/v1/memes`; `rooms/*/chat` → после чата комнаты;
   `rooms/*/teams` → после составов; `rooms/*/wordSubmissions` → после слов; `rooms` → последней,
   вместе с движком партии. Семь шагов вместо одного, и каждый закрывает свой кусок дыры в день выкатки.
3. **Маршрутизация внутри `public/db.js`.** Шапка файла прямо говорит, что имена
   `doc/onSnapshot/runTransaction/setDoc` сохранены намеренно, потому что их зовут из семи файлов.
   Это готовый анти-коррупционный слой: `setDoc(rooms/{id}/players/{uid})` внутри `db.js` превращается
   в `PUT /api/v1/rooms/{id}/players/me` — по одной операции за релиз, обратимо, без правок
   в `app-core.js`. Так закрываются 31 прямая запись; неперекладываемыми остаются 16 транзакций —
   их клиентские функции (`beginTurn`, `guessed`, `skipWord`, `endTurn`, `nextTurn`, `saveWords`,
   `collectWordsAndStart`, `backToSetup`, `resetRoom`, `createRoom`, `joinTeam`, `leaveTeam`,
   `addTeam`, `deleteTeam`) переписываются на вызовы адресов из §4.8 поштучно.

### 6.4 Что теряет и получает фронт

- `public/db.js` теряет `setDoc`/`updateDoc`/`deleteDoc`/`writeBatch`/`runTransaction`
  и на переходный период становится маршрутизатором путей в доменные команды; затем удаляется.
- `onSnapshot` меняет сигнатуру с пути на имя канала; шесть подписок игрового экрана становятся одной.
- Оптимистичная отрисовка (`makeOptimisticWordPlan`, `state.wordActionQueue`, ~200 строк в `app-core.js`)
  не выбрасывается: каждый ход-эндпоинт возвращает `TurnStateResponseDTO` целиком, поэтому UI
  подтверждает предсказание ответом, а не ждёт снимка по WS. **Розыгрыш следующего слова уезжает
  на сервер, поэтому `drawRandomWord(bag)` в клиенте удаляется, а `plan.nextWord` берётся из ответа** —
  это самая заметная правка игрового экрана, и её надо планировать отдельной задачей.
- `POST /api/db/write` после закрытия последней коллекции отвечает `410` с кодом `USE_DOMAIN_COMMAND`
  и подсказкой маршрута в `details` — чтобы забытый путь был виден в логах, а не превращался в 404.
- `GET /api/db/document` и `POST /api/db/query` живут один релиз под `@Deprecated` и заголовком `Sunset`,
  удаляются после того, как в логах пропадут обращения.

---

## 7. Конвенция DTO и Swagger

### 7.1 Именование и форма

- Пакет: `ru.hothat.<домен>.api.dto`. Шесть сегодняшних пустых каталогов `dto/` удаляются.
- **Одна операция — одна пара**: `<Действие>RequestDTO` + `<Действие>ResponseDTO`. Переиспользование
  между операциями запрещено. Единственное разрешённое разделение — **композиция**: общий вложенный
  `record` (`RoomSummaryView`, `PlayerCardView`, `TurnView`) включается в несколько ответов как поле.
  Ответы `bff` собираются исключительно композицией доменных response-DTO, поэтому форма данных
  описана в одном месте.
- Форма — java `record` и для запроса, и для ответа. Списки — всегда объект с курсором
  `{items, nextCursor, limit}`, никогда голый массив (D2).
- Ответ без тела — `204`; ответ с телом — всегда именованная схема, никогда `Map<String,Object>`.
- **Одна операция — одна форма ответа.** Сегодняшние несовместимые ветки (`matchmake`, `record_result`,
  `sync_game_presence`, `setup_host_watch`, `ranked_autostart`, `recordings start/finish`,
  `sync_room_nickname`, `send_room_invite`, `test-bots tick`) сводятся к одной записи с полем-перечислением
  (`state`, `outcome`) и nullable-полями. Клиент перестаёт различать ответы по наличию ключа.

### 7.2 Валидация

- Bean-валидация выражает обязательность (`@NotNull`, `@NotBlank`), необязательность — обёрточным типом
  с единственным местом дефолта. `spring.jackson.deserialization.fail-on-unknown-properties=true`
  включается после удаления `room_name` из двух мест фронта.
- Общие форматы — собственные аннотации: `@RoomId`, `@Nickname` (`^[A-Za-z][A-Za-z0-9_]{2,19}$`),
  `@ImageDataUrl(max=…)`, `@MemeId`, `@DivisionLanguage`. `RoomId` — value-тип в сигнатурах use-case:
  тогда «не проверил идентификатор» перестаёт компилироваться.
- Закрытые наборы — `enum` в DTO (`WeaponType`, `AnalyticsEventType`, `HostActivityKind`,
  `DepartureReason`, `PreflightIntent`), а не свободные строки. Jackson отвергает неизвестное значение
  до входа в контроллер — поэтому «нет `switch` по строке» выполняется буквально.
- Каскадный `@Valid` обязателен на вложенных структурах (сегодня забыт на `QueryRequestDTO.where`, A8);
  проверяется ArchUnit-правилом «поле-коллекция DTO аннотировано `@Valid`».
- Сущности `model/**` не появляются ни в сигнатурах, ни в ответах.

### 7.3 Swagger

1. `@Schema(description, example)` на каждом поле каждого DTO; `allowableValues` для enum,
   `pattern` для форматов; `therapi-runtime-javadoc` в `annotationProcessorPaths`, чтобы уже написанный
   javadoc попадал в спецификацию.
2. `@SecurityRequirement(name="Bearer")` объявляется **глобально** в `SwaggerConfig`; публичные операции
   помечаются пустым `@SecurityRequirements`. Состояние по умолчанию совпадает с
   `.anyRequest().authenticated()`.
3. `OpenApiCustomizer` дописывает во все операции 401/403/500 со схемой `ErrorDTO`; точечные
   `@ApiResponse` — на особых кодах (409 `NICKNAME_TAKEN`, 402 `SABOTAGE_LIMIT_REACHED`,
   410 `ROOM_INVITE_EXPIRED`, 429 `WEAPON_COOLDOWN`).
4. `@Parameter` на каждом `@RequestParam`/`@PathVariable`/`@RequestHeader`;
   `@RequestParam Map<String,String>` не допускается.
5. **Теги springdoc = домены**, один `GroupedOpenApi` на домен плюс сводная группа `all`:
   `identity`, `profile`, `friendship`, `messaging`, `ranked-team`, `matchmaking`, `room`, `match`,
   `sabotage`, `meme`, `recording`, `ranking`, `video`, `moderation`, `observability`, `maintenance`,
   `platform`, `speech`, `test-bots`, `screens`. Группы `db`/`portal`/`game`/`ops` из §5.4 аудита
   **не заводятся** — это имена старых диспетчеров, а не доменов.
   `springdoc.swagger-ui.path=/api/docs`, `tagsSorter=alpha`, `operationsSorter=method`.
6. `/v3/api-docs/**` и `/swagger-ui/**` — под `hasRole('ADMIN')` либо выключены в проде
   переменной `SPRINGDOC_API_DOCS_ENABLED=false` (A13).
7. Протокол `/ws/v1/**` — отдельным AsyncAPI-документом со ссылкой через `.externalDocs(...)`:
   восемь каналов, у каждого пара DTO (hello, event).

### 7.4 Ошибки

`enum ErrorCode(int status, String text)` — код, статус и текст в одном месте; `ApiException.of(ErrorCode)`
— единственный конструктор (перегрузка `of(String)`, молча дающая 400, удаляется);
`record ErrorDTO(String error, String code, Map<String,Object> details)`. Переменная часть уезжает в
`details`: `{"fields":["memeCycleCursor"]}` вместо `FIELD_UNKNOWN: memeCycleCursor`.
Клиент ветвится только по `code` — `errorMessage()` в `app-core.js:7860-7901` переписывается на
`switch (error.code)`. WebSocket отдаёт ту же форму.

---

## 8. Порядок работ

Одиннадцать фаз. Правило на весь переход: **новые маршруты поднимаются рядом со старыми**, старый
диспетчер остаётся тонким адаптером и делегирует в тот же use-case, помечается `@Deprecated` +
`deprecated: true` в `@Operation` + заголовок `Sunset`. Откат любой фазы = выкатка предыдущей сборки
бекенда (маршруты аддитивны) плюс выключение флага на фронте.

| Фаза | Содержание | Что делает фронт | Как откатиться |
|---|---|---|---|
| **0. Заплатки** (1–2 дня) | Фаза 0 аудита без изменений: членство в `checkWrite`, `memeLibrary` → `SERVER_ONLY_WRITES`, удаление `cleanup_rooms`, `log.info` со ссылкой, `@Valid` на `where`, потолок тела, `MessageDigest.isEqual`, `requirePlayer` в `setupHostWatch`, Bearer на `/api/auth/link` | Ничего. Проверить, что ни один клиентский путь не писал ставшие серверными поля | Откат одного коммита |
| **1. Фундамент** (1 неделя) | `ErrorCode` + `GlobalExceptionHandler extends ResponseEntityExceptionHandler`; `Clock`/`RandomSource`; `@EnableMethodSecurity` + четыре машинные роли через фильтры; ArchUnit-правила; **golden-снимки** ответов `/api/db/document` и `/api/db/query` по всем 14 шаблонам `CollectionRegistry` как единственная регрессионная база | Правка `public/auth.js:178-195` (401→403 при неверном текущем пароле); удаление `room_name` из `livekit.js:541`, `live-preview.js:104` | Флаги в конфигурации; матчеры остаются прежними |
| **2. Миграция схемы** (2 недели) | `room` (103 колонки) → `room` + `match_state` + `matchmaking_ticket` + `test_bot_run` + `match_sabotage_state`; `app_user` (36) → `user_account` + `player_profile`; `room_player` (27) → `room_player` + `player_arsenal`; `room_team.score` → `match_state.scores`; `ranked_team_member`. Период двойной записи, представления для обратной совместимости чтения | Ничего: `/api/db` читает через представления | Обратные миграции подготовлены заранее; двойная запись позволяет вернуться на старые колонки |
| **3. Машинные акторы** (1 неделя) | `recording` + рекордер + вебхук Egress, `observability`, `maintenance`. Мутирующие GET → POST, подпись из query в заголовок | `recording-view.js:9` перекладывает `sig` в заголовок; `admin.js` — два запроса вместо `scope=all` | Старые адреса живы; рекордер — наш собственный клиент |
| **3а. Ранний BFF** (опционально, 3 дня) | `GET /api/v1/screens/shell` и `/screens/rooms/{roomId}` поверх **старых** сервисов | Снимает 7 вызовов `ensure_profile` и 6 стартовых снимков; немедленный выигрыш по задержке | Просто не звать новый адрес |
| **4. Медиа и платформа** (1 неделя) | `meme`, `platform`, `speech`. `memeLibrary` → `SERVER_ONLY_WRITES` | `meme-s3-only.js`, `features.js`, `app-core.js:6424` — точечные правки | Коллекция возвращается в белый список одной строкой |
| **5. Личность и модерация** (1 неделя) | `identity`, `moderation`, `video` (три адреса токена вместо `role`) | `auth.js` целиком, `livekit.js` три места вызова токена | Старые `/api/auth/**` и `/api/token` живы |
| **6. Профиль** (1–2 недели) | `profile`; перед этим — свести шесть копий хелпера `api(action, data)` (`portal.js:19`, `home/home.js:44`, `friends/friends.js:6`, `realtime-social.js:23`, три инлайна в `portal-shell.js`) в один клиентский модуль, отдающий и статус ответа | После сведения — миграция домена становится правкой одного файла | По одному вызову за раз |
| **7. Социальное и команды** (2 недели) | `friendship`, `messaging`, `ranked-team`, `matchmaking`, `ranking` | `portal.js`, `friends.js`, `realtime-social.js` — по одному действию | Старые `action` живы |
| **8. Комната** (3–4 недели) | `room` целиком: создание, вход, зрители, составы, чат, приглашения, хозяин. Коллекции `rooms/*/players`, `spectators`, `teams`, `chat` → `SERVER_ONLY_WRITES` по мере переезда | `db.js` маршрутизирует путь → команду; `app-core.js` не правится | Коллекция возвращается в белый список; `db.js` — прежняя ветка |
| **9. Партия и диверсии** (4–6 недель) | `match`, `sabotage`, `realtime`-каналы. 16 клиентских транзакций переписываются; `rooms` → `SERVER_ONLY_WRITES`; шесть подписок → один канал | Самая тяжёлая правка: игровой цикл и оптимистичная отрисовка. Выкатывается **целиком**, по частям нельзя | Флаг `HH_GAME_V1` на фронте переключает игровой модуль между старым и новым путём; бекенд держит оба до снятия флага |
| **10. Тест-боты** (1 неделя) | `test-bots` переводится на командные порты `match`/`sabotage` вместо прямой записи | `test-mode.js` — три вызова | Старый `POST /api/test-bots` жив |
| **11. Уборка** (1–2 недели) | Удаление `compat`, `/ws/documents`, моста `DocumentChangedEvent`, ~19 церемониальных интерфейсов; `BFF` переводится на новые домены; `Sunset` наступает | `db.js` удаляется | Точки возврата больше нет — фаза выполняется после того, как в логах месяц нет обращений к старым адресам |

**Когда удалять старые маршруты.** Каждый старый адрес живёт минимум один полный релизный цикл после
того, как новый выкачен, и удаляется, когда счётчик обращений к нему на дашборде `observability`
держится на нуле две недели. `POST /api/db/write` — исключение: он закрывается по коллекциям сразу,
как только соответствующий домен выкачен, потому что каждая открытая коллекция — это открытая A1.

---

## 9. Оценка объёма

Считано по таблице §4: 207 эндпоинтов, из них 8 каналов WS и 4 переходных.

| Домен | Контроллеры | Use-case | Request-DTO | Response-DTO (вкл. вложенные) | Мапперы | Порты | Всего классов |
|---|---|---|---|---|---|---|---|
| identity | 6 | 11 | 8 | 12 | 3 | 2 | ~42 |
| profile | 11 | 18 | 13 | 23 | 4 | 3 | ~72 |
| friendship | 4 | 9 | 3 | 12 | 2 | 2 | ~32 |
| messaging | 5 | 7 | 4 | 10 | 2 | 2 | ~30 |
| ranked-team | 9 | 12 | 5 | 18 | 3 | 3 | ~50 |
| matchmaking | 4 | 5 | 3 | 4 | 1 | 3 | ~20 |
| room | 15 | 33 | 19 | 39 | 6 | 4 | ~116 |
| match | 10 | 17 | 6 | 24 | 4 | 4 | ~65 |
| sabotage | 6 | 9 | 6 | 12 | 2 | 3 | ~38 |
| meme | 8 | 12 | 8 | 13 | 3 | 2 | ~46 |
| recording | 9 | 19 | 8 | 24 | 4 | 3 | ~67 |
| ranking | 2 | 4 | 4 | 8 | 2 | 2 | ~22 |
| video | 4 | 4 | 3 | 3 | 1 | 2 | ~17 |
| moderation | 5 | 6 | 3 | 6 | 2 | 3 | ~25 |
| observability | 8 | 9 | 5 | 11 | 3 | 2 | ~38 |
| maintenance | 5 | 5 | 0 | 5 | 1 | 4 | ~20 |
| platform | 4 | 4 | 2 | 4 | 1 | 1 | ~16 |
| speech | 1 | 1 | 1 | 1 | 0 | 1 | ~5 |
| test-bots | 3 | 4 | 2 | 4 | 1 | 3 | ~17 |
| realtime | 8 | 8 | 8 | 8 | 2 | 0 | ~34 |
| bff | 6 | 6 | 0 | 6 (композиция) | 0 | 0 | ~18 |
| compat | 4 | 3 | 2 | 3 | 1 | 0 | ~13 |
| **Итого** | **137** | **206** | **113** | **250** | **47** | **49** | **~802** |

Плюс: ~12 доменных политик (`RoomAccessPolicy`, `TurnRules`, `WeaponRegistry`, …), ~9 authz-бинов,
~8 событий со слушателями, 5 фильтров ролей, сущности и репозитории под 8 новых таблиц (~30 классов),
конфигурация Swagger. **Итог ≈ 855 новых классов** против сегодняшних 216 java-файлов и 19 545 строк.

**Календарь.** Сумма фаз §8 — 18–26 недель при одном исполнителе, то есть **4–6 месяцев**, из которых
2–3 месяца API существует в двух экземплярах. Оценка «фаза 3, 2–4 недели» из §6 аудита относилась
к переименованию маршрутов и к этому объёму неприменима: она не учитывала ни перенос игрового движка
с клиента, ни миграцию схемы. Тестов в проекте нет, поэтому фаза 1 (golden-снимки) не опциональна —
без неё проверять фазы 8–9 нечем.

---

## 10. Открытые вопросы к заказчику

| № | Вопрос | Варианты | Рекомендация |
|---|---|---|---|
| 1 | **Режем ли таблицы?** `room` (103 колонки), `app_user` (36), `room_player` (27) сегодня пишут по 5–7 сервисов. | (а) Резать (фаза 2, 2 недели, необратимые миграции на живых данных); (б) оставить одну таблицу и владение по группам полей с аннотацией `@FieldOwner` + ArchUnit. | **(а)**. Вариант (б) — правило, которое держит не компилятор, а обещание; оно сломается на третьем спринте, и тогда все границы останутся только в именах пакетов. Если (а) неприемлемо по риску — тогда честнее сократить план до фаз 0–7 и не трогать `room`/`match` вовсе. |
| 2 | **Диверсии: один эндпоинт или тринадцать?** | (а) `POST …/sabotages` с `enum WeaponType` + `WeaponRegistry` (в плане); (б) тринадцать адресов по видам оружия. | **(а)**. В контроллере ветвления нет ни при (а), ни при (б) — enum проверяет Jackson. Но при (б) четырнадцатое оружие становится изменением API, а клиент обязан держать таблицу «оружие → адрес». Это буква требования против инженерного смысла. |
| 3 | **Матчмейкинг: опрос или событие?** Сегодня `home/home.js:99` опрашивает раз в 5 с до 60 с, `portal.js:127` — раз в 3 с до 2 минут. | (а) Тикет + `GET` статуса (в плане, опрос сохраняется); (б) тикет + событие в `/ws/v1/lobby`, опрос убирается. | **(а) сейчас, (б) в фазе 9**. Вариант (б) правильнее, но требует ещё одной переделки клиента и не должен блокировать переезд домена. |
| 4 | **Что открыть гостю?** Сегодня токен выдаётся бесплатно и отвергается всеми защищёнными маршрутами (A7) — это фактическая, но случайная защита. | (а) Гость видит лобби, превью комнаты, апгрейд, согласия, аналитику; (б) гость остаётся ничем; (в) гость играет наравне. | **(а)**. Это продуктовое решение, а не архитектурное; в плане отражено только то, что `upgrade` требует Bearer гостя. Без ответа `ROLE_GUEST` останется ролью без прав. |
| 5 | **Сколько экранов в `bff` и остаются ли доменные `GET` публичными?** | (а) Шесть экранов, доменные `GET` публичны (в плане); (б) экраны — единственный вход, доменные `GET` внутренние; (в) `bff` не заводить, платить лишними запросами. | **(а)**. Риск (а) — два контракта на одни данные; он снимается правилом «ответ экрана собирается композицией доменных response-DTO», то есть форма описана один раз. Вариант (в) даёт ~12 запросов на старте портала вместо трёх. |
| 6 | **Спорные границы, по которым эксперты не сошлись.** `room-invite` и `preflight` — домены или модули? `test-bots` — домен или драйвер? `recorder` — домен или модуль `recording`? | (а) Как в плане: приглашение — модуль `room`, префлайт — модуль `ranked-team`, рекордер — модуль `recording`, тест-боты — домен без права прямой записи; (б) отдельные домены для всех четырёх (получится 25 доменов). | **(а)**. Критерий один: у модуля нет своей таблицы либо его таблица неотделима от родительской. `game_recording` физически одна таблица (`recorderReadyAtMs`, `recorderStartSignalAtMs`, `recorderLivekitIdentity` — её колонки), поэтому два домена над ней означали бы двух писателей. |
| 7 | **Готовы ли к 4–6 месяцам и ~850 новым классам?** | (а) Весь план; (б) фазы 0–7 (всё, кроме комнаты и партии) — примерно 2 месяца, дыра A1 закрывается частично, `/api/db/write` остаётся для `rooms/*`; (в) только фазы 0–2. | **(а), если решение по вопросу 1 — «резать»; иначе (б)**. Вариант (б) — честная остановка: портал распущен, машинные акторы и медиа переехали, игровой экран остаётся как есть. Вариант (в) не даёт разделения на домены вообще и годится только как подготовка. |
