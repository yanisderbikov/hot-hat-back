# Кадр канала комнаты → экран: контракт порции B

Порция A закончена на сервере: кадр `/ws/v2/room/{roomId}` несёт всё, что
игровому экрану раньше давали шесть подписок старого шлюза, и собирается на
слушателя. Порция B — переезд `app-core.js` с подписок на кадр. Этот документ
— договор между ними: что в кадре называется иначе, чем в документе комнаты,
где экран читает старое имя и что именно должен сделать адаптер.

> **Состояние.** Старого канала на сервере больше нет: `/ws/documents` и весь
> документный стек снесены, экран комнаты получает состояние только кадром.
> Пары «шесть документов ↔ кадр» по 34 моментам партии, снятые до сноса, лежат
> в репозитории фронта (`test/fixtures/room-frames/index.json` и `moments/`);
> ссылки на подписки `app-core.js` ниже описывают код до переезда.

Имена в кадре — имена API v2 (`uid`, `teamId`, `…AtMs`, `clipId`), и они
намеренно не подгонялись под ключи документа: те же записи (`RoomSeatView`,
`RoomSummaryView`, `RoomChatMessageView`, `SabotageEventView`) отдают
двадцать адресов HTTP, и переименовать поле «под экран» значило бы сломать
их все ради одного потребителя, который всё равно переводит имена на границе
(`adoptRecorderPlayers`, `matchStateToRoomFields`, `normalizeSabotageEvent` уже
существуют). Правило порции B поэтому одно:

> **Кадр переводится в `state.*` одним адаптером и больше нигде.** Ни одна
> строка вне адаптера не читает кадр напрямую, и ни одно поле кадра не
> разливается в `state.room` оператором `...`.

Ссылки на строки `app-core.js` и `livekit.js` — по состоянию на день
порции A; они указывают на место чтения, а не фиксируют номер.

## 1. Форма кадра

```text
{ type: "hello" | "room",
  room: {
    snapshot: { room, players[], teams[], spectators[], viewerSeat },   // = GET /api/v2/room/{roomId}
    chat:     { items[], nextCursor, limit },                            // = GET …/chat-messages
    match:    { match, arsenal | null, ammo[], clips[] },                // = GET /api/v2/game/{roomId}
    words:    { words[], mine, total } } }                               // = PUT …/word-submissions/me
```

Каждая часть — форма своего адреса HTTP поле в поле, поэтому адаптер частей
пригоден и для ответов этих адресов: `frame.room.match` и ответ
`GET /api/v2/game/{roomId}` кормят один и тот же `matchViewToRoom`. Сегодня
`applyMatchStateToRoom` принимает только `match.match`; в порции B он
принимает четвёрку целиком.

## 2. `snapshot.room` → `state.room`

Разливать `snapshot.room` в `state.room` нельзя из-за одного столкновения:
`RoomSummaryView.turnDurationSeconds` — **настройка комнаты**, а
`state.room.turnDurationSeconds` экран уже заполняет из
`turn.durationSeconds` (`app-core.js:12776`) — **длительностью текущего хода**,
которая после снятия паузы равна остатку. При слиянии победило бы то, что
положили последним. Поэтому — только поимённо:

| В кадре | В `state.room` | Где читают | Примечание |
|---|---|---|---|
| `roomId` | — | | экран держит `state.roomId` |
| `name` | `name` | | |
| `phase` | `phase` | | |
| `gameMode` | `gameMode` | 30 чтений | |
| `ranked` | `ranked` | 9 чтений | |
| `privateRoom` | `isPrivate` | `1088`, `8976`, `9156` | |
| `testRoom` | `isTestRoom` | 115 чтений (`1055`, `1747`, `1755`, `1768`, `2803`, `3323`, `11585`…) | сегодня переводится один раз, в `startGame` (`12622`) |
| `divisionLanguage` | `divisionLanguage` | | |
| `gameLanguage` | `gameLanguage` | | |
| `capacity` | `maxPlayers` | `1083`, `1116`, `9037`, `9153`, `11981`, `12042` | все чтения вида `maxPlayers \|\| maxParticipants`; второго имени класть не надо |
| `turnDurationSeconds` | **`turnDuration`** | `9002`, `9514` | **не** `turnDurationSeconds` — см. выше |
| `gameNumber` | `gameNumber` | 33 чтения | |
| `recordingRequested` | `recordGame` | `1378`, `1391`, `4447`, `8836`, `8862` | |
| `hostUid` | `createdBy` | `1087`, `1115`, `1204`, `2373`, `2803`, `8794`, `8822-8825` | |
| `hostLastSetupActivityAt` | `hostLastSetupActivityAt` | `1089`, `9155`, `13691` | единственное поле без суффикса `…AtMs` — оставлено ключом документа намеренно |
| `createdAtMs` | — | | экран не читает |

Чего в паспорте нет и не будет:

- `closedAt` — экран проверяет `closedAt || phase === "closed"` (`8787`);
  `CloseRoomUseCase` ставит `phase=closed` и `closedAt` одной записью, фазы
  достаточно.
- `wordCount` — это `words.total`, см. §7.
- `turnStartedAt` — экран уже берёт `turnEndsAt` из `turn.deadlineMs`, а
  `getTurnDeadline` умеет этот фолбэк.
- `country` / `region` / `city` — шапка комнаты их рисует (`12184-12198`), но
  ни у сущности `Room`, ни у `CreateRoomRequestDTO` их нет. Открытый вопрос
  заказчику: убрать из UI или завести.

## 3. `snapshot.players` / `spectators` / `teams` → `state.players` / `state.spectators` / `state.teams`

Строки состава ключуются на экране по `id`, `isTestBot`, `lastSeenAt`
(`8431`, `8441-8442`, `8576`, `8597`, `9244`, `9330-9337`, `11739`;
`livekit.js:275-278`, `:387`, `:2245-2250`). Кадр отдаёт `uid`, `testBot`,
`lastSeenAtMs`. Ровно этот перевод экран уже делает для машинного ответа
рекордера — `adoptRecorderPlayers` (`1701-1708`); в порции B он становится
общим адаптером строк снимка, а не частным случаем рекордера.

| Строка | В кадре | В `state.*` | Как есть |
|---|---|---|---|
| игрок | `uid` → `id`, `testBot` → `isTestBot`, `lastSeenAtMs` → `lastSeenAt` | | `name`, `avatarDataUrl`, `teamId`, `alive`, `cameraEnabled`, `microphoneEnabled` |
| зритель | `uid` → `id`, `lastSeenAtMs` → `lastSeenAt` | | `name`, `avatarDataUrl` |
| команда | `teamId` → `id` | | `name`, `order`, `score`, `memberUids`, `rankedTeamId` |

Строка игрока в кадре **не несёт боезапаса** — он собирается из трёх
источников, см. §5. `alive` считает сервер: порог живости у экрана свой
больше не нужен.

**Зрители — без preview.** Место с `preview=true` заводит наведение курсора
на карточку комнаты в лобби (`RoomPreviewSeats`); прежняя подписка
`rooms/{id}/spectators` отдавала такие строки экрану, и значок «Зрителей: N»
считал прохожих. `RoomProjections.spectators` их отсеивает, поэтому
`snapshot.spectators` и `state.spectators` могут быть короче прежнего
списка — это решение проекции, а не потеря.

`state.teams` берётся из `snapshot.teams`. В `match.match.teams` едет тот же
счёт (`TeamScoreView`, тот же `RoomTeamPort`); второй раз сливать его в
`state.teams` не надо.

## 4. `match.match` → игровые поля `state.room`

`matchStateToRoomFields` (`12761-12794`) уже переводит: `phase`, `wordsLeft`,
`currentTeamId`, `teamOrder`, `rosters → teamRosters`,
`playerNames → gamePlayerNamesByUid`, `turn.* → turnId / explainerUid /
explainerName / guesserUid / guesserName / turnEndsAt (deadlineMs) /
turnDurationSeconds (durationSeconds) / currentTurnScore (score) /
currentWord`, `appeal`, `appealEndsAt`, `pause.* → gamePaused / hostPaused /
pauseReason / pauseMissingUids / pauseMissingNames / pauseStartedAtMs`.

Чего в нём **нет** и что порция B обязана добавить — иначе поле в `state.room`
не попадёт вовсе:

| В кадре | В `state.room` | Где читают | Примечание |
|---|---|---|---|
| `gameNumber` | `gameNumber` | | сегодня — только в аварийной ветке (`1497`) |
| `lastSabotage` | `sabotageEvent` | `8794`, `11656` | через `normalizeSabotageEvent`; сегодня — только в аварийной ветке (`1502`) |
| `termination` | `technicalTermination` | `11955-11956` | сегодня — только в аварийной ветке (`1503`) |
| `pause.turnRemainingMs` | `pausedTurnRemainingMs` | `9507` | иначе таймер на паузе покажет 00 |
| `pause.appealRemainingMs` | `pausedAppealRemainingMs` | `9645`, `9669` | иначе retry апелляции считается от нуля |
| `sabotageEventsRecent[]` | `sabotageEventsRecent[]` | `11655-11660` | **каждый элемент** через `normalizeSabotageEvent`, см. §4.1 |
| `sabotageLocks` | `sabotageLocks` | `1772-1789` | как есть; `replacementRecordingUntil` — свой срок, у `sabotageLocksUi` эта ветка есть (`1780`) |
| `lastTurn` | `lastTurn` | `11739-11741` | как есть: `turnId`, `teamId`, `score`, `finalScore` |
| `lastGuessedWord` | `lastGuessedWord` | `9389` | как есть |
| `lastActionAtMs` | `lastActionAtMs` | `1646-1653` | как есть |
| `testBotIds` | `testBotIds` | `1200-1235` | как есть |
| `testOwnerExplainerGameNumber`, `…TurnNumber` | те же | `4294-4318` | как есть |
| `testRoom` | `isTestRoom` | | дубль паспорта; в партии — для `sabotageAllowed` |
| `gameMode`, `ranked` | те же | | дубли паспорта |

**`turn.*` вне `active` — `null`/`0`.** `MatchViewAssembler.turn()` отдаёт ход
только в `active`; прежний документ держал `turnId`, `explainerUid`,
`explainerName`, `guesserUid`, `guesserName`, `currentTurnScore` закрытого хода
и в `appeal`. Адаптер пишет туда `null` (`currentTurnScore` — `0`), имя
закрытого хода — `appeal.turnId`; менеджер Подмены и тулбар показываются
только в `active`, `renderAppeal` читает `appeal.*`.

После этого аварийная ветка `refreshFollowerRoomSnapshotOnce` (`1495-1504`)
перестаёт добавлять что-либо поверх `matchStateToRoomFields` — обе ветки
кормятся одним кодом, ради чего функция и выносилась.

### 4.1 Идентификатор события: `eventId`, не `id`

`SabotageEventView` зовёт идентификатор `eventId` — как все записи области
(`clipId`, `turnId`, `wordId`). Экран ждёт `id`: `processSabotageEvent`
выходит на `if (!event?.id) return` (`11437`), а
`processSabotageEventsFromRoom` кладёт в `Map` только `if (event?.id)`
(`11657`). Сегодня `normalizeSabotageEvent` (`10228`) применяется к
`lastSabotage` (`1502`) и к ответам выстрелов, но **не к массиву**: без
нормализации каждого элемента `sabotageEventsRecent` отбрасывается молча и
пропущенные при переподключении эффекты не доигрываются.

Адаптер: `sabotageEventsRecent: (match.sabotageEventsRecent ?? []).map(normalizeSabotageEvent)`.

## 5. Боезапас: три источника → поля строки игрока

Экран читает боезапас со строки игрока: `player.arsenal` для плиток
(`livekit.js:2249`, `:2483`), `memeLoadout` / `memeAvailableIds` /
`memeReserveIds` / `memeRecycleQueue` / `sabotageCooldownUntil` для своей
панели (`9838-9855`, `9980`, `10014-10015`, `10155-10156`, `8925`). Кадр
разводит это по охвату, и адаптер собирает строку из трёх мест:

1. `snapshot.players[i]` — место (§3);
2. `match.ammo[]` по `uid` — **чужое и своё**: `ammo` (десять счётчиков) и
   `loadoutCharged`;
3. `match.arsenal` — **только своё** (`player.id === me`): `ammo`, `loadout`,
   `available`, `reserve`, `recycle`, `cooldownUntilMs`.

| Источник | В кадре | В строке игрока | Кому |
|---|---|---|---|
| `match.ammo[uid]` | `ammo` | `arsenal` | всем; затем `decoratePlayerArsenalForUi` как раньше |
| `match.ammo[uid]` | `loadoutCharged` | `loadoutCharged` | всем; **новое поле**, см. ниже |
| `match.arsenal` | `ammo` | `arsenal` | себе (те же счётчики, что в `ammo[me]`) |
| `match.arsenal` | `loadout` | `memeLoadout` | себе |
| `match.arsenal` | `available` | `memeAvailableIds` | себе |
| `match.arsenal` | `reserve` | `memeReserveIds` | себе |
| `match.arsenal` | `recycle` | `memeRecycleQueue` | себе |
| `match.arsenal` | `cooldownUntilMs` | `sabotageCooldownUntil` | себе |

**Проверки готовности переписываются, а не переименовываются.**
`allPlayersHaveLoadouts` (`5152-5155`) и `startReadinessBlockers` (`11995`)
проверяют `Array.isArray(player.memeLoadout)` и «пять разных». Чужой обоймы в
кадре нет и не будет — ради этого поле и убрано, — поэтому обе проверки
переходят на `player.loadoutCharged === true`. Сервер считает его тем же
правилом (`LoadoutRules.charged`: пять разных непустых слотов), так что для
своей строки оба способа дают одно и то же.

`usedMemeIds` и `memeCycleCursor` в кадре нет ни для кого: первое экран читает
только фолбэком при отсутствии `memeAvailableIds` (`9838-9855`), а
`available` в `ArsenalView` есть всегда; второе нигде не рисуется.

## 6. `match.clips` → `state.room.replacementRecordings`

Документ держал карту `{recordId: {id, …}}`, кадр отдаёт список
`ReplacementClipView{clipId, attackerUid, targetUid, recordedTurnId,
gameNumber, createdAtMs, ready}` — и только **свои** клипы.
`syncPublicReplacementRecordsFromRoom` (`3322-3338`) обходит `Object.values`
(список пройдёт) и берёт `item.id` (`3328`) — без перевода каждый клип
пропускается.

Адаптер: `replacementRecordings: Object.fromEntries(clips.map(c => [c.clipId, { ...c, id: c.clipId }]))`.
Фильтр по `attackerUid === me` в самой функции остаётся — он теперь
безвреден.

## 7. `words` → `state.myWords` и `state.room.wordCount`

| В кадре | В `state` | Где читают |
|---|---|---|
| `words.words[]` | `state.myWords` | `8957`, поле ввода слов |
| `words.total` | `state.room.wordCount` | `9012`, `9324`, `11999` (блокер «меньше пяти слов»), `8851` |
| `words.mine` | — | это `myWords.length` |

У зрителя `words.words` пуст, `total` — общий.

## 8. `chat.items` → `state.chatMessages`

`renderRoomChat` (`8660-8680`) читает `role`, `attachment` (с обязательным
`kind === "image"`, `8618-8619`), `createdAt`, `avatarDataUrl`. В кадре —
`seat`, `image` (без `kind`), `createdAtMs`; аватара у сообщения нет.

| В кадре | В сообщении экрана | Примечание |
|---|---|---|
| `messageId` | `id` | |
| `uid`, `name`, `text`, `testBot` | те же | |
| `seat` | `role` | `"player"` / `"spectator"` |
| `image` | `attachment` | `image ? { kind: "image", dataUrl, width, height, name: fileName } : null` |
| `createdAtMs` | `createdAt` | число; `timestampToMillis` (`8134`) его принимает |
| — | `avatarDataUrl` | не класть: `renderRoomChat` уже берёт аватар из `chatParticipantByUid` (`8594-8598`), то есть из `snapshot.players` / `spectators` того же кадра |

`items` идут от старых к новым — в том порядке, в каком `state.chatMessages`
и ждёт (`8947` разворачивал документы именно в него). Вглубь листают по HTTP
курсором `nextCursor`.

## 9. Кому что приходит

Кадр собирается на каждого подписчика отдельно, право на кадр (`requireSeat`:
игрок либо зритель) перепроверяется на каждом кадре. Всё, что ниже, —
свойство сборщика `MatchViewAssembler`, а не договорённость с экраном, и
`RoomChannelFrameE2ETest` держит это под присмотром тремя-четырьмя сокетами
на одной партии.

| Поле | Кто видит |
|---|---|
| `match.match.turn.currentWord` | только объясняющий; остальным `null` |
| `match.arsenal` | только владелец; у зрителя и не-игрока `null` |
| `match.clips` | только свои; у остальных `[]` |
| `match.match.sabotageLocks.replacementRecordingUntil` | только свой срок; чужие — `0` |
| `match.match.lastSabotage`, `sabotageEventsRecent[]` | выстрелы — все; **съёмка Подмены (`replacement_record`) — только снимающий и снимаемый**; третьему лицу последней остаётся предыдущая видимая, а не `null` |
| `match.match.appeal.words[].myVote` | свой голос |
| `words.words` | свои; у зрителя `[]` |
| `match.ammo[]` | все: `uid`, десять счётчиков, `loadoutCharged` — без единого идентификатора мема, кулдауна и клипа |
| `snapshot.*`, `chat.*`, `words.total`, остальное `match.match.*` | все за столом и зрители |

Экран на `replacement_record` реагирует только у снимаемого и снимающего
(`11547-11552`), так что третьему лицу событие и не нужно. Канал данных
LiveKit подчинён тому же правилу: адресаты пакета берутся из события
(`SabotageEvent.audience()` → `destination_identities`), съёмка уходит
двоим, выстрелы — всей комнате. Правило одно на кадр, ответ адреса и пакет
(`SabotageEvent.visibleTo`), поэтому обойти завесу другим путём доставки
нельзя. Прежний движок и боты (`LiveKitService.sendSabotage`) стреляют
только публичным оружием и рассылают, как раньше, всем.

## 10. Эскиз адаптера

Не код, а перечень мест, которые порция B сводит в одну функцию:

```js
function adoptRoomFrame(frame) {                      // frame = payload.room
  const me = String(state.user?.uid || "");
  const ammoByUid = new Map(frame.match.ammo.map((row) => [row.uid, row]));
  const own = frame.match.arsenal;

  state.players = frame.snapshot.players.map((seat) => decoratePlayerArsenalForUi({
    ...adoptSeat(seat),                                // = adoptRecorderPlayers для одной строки
    arsenal: ammoByUid.get(seat.uid)?.ammo ?? {},
    loadoutCharged: ammoByUid.get(seat.uid)?.loadoutCharged === true,
    ...(seat.uid === me && own ? {
      arsenal: own.ammo, memeLoadout: own.loadout, memeAvailableIds: own.available,
      memeReserveIds: own.reserve, memeRecycleQueue: own.recycle, sabotageCooldownUntil: own.cooldownUntilMs,
    } : {}),
  }));
  state.spectators = frame.snapshot.spectators.map(adoptSeat);
  state.teams = frame.snapshot.teams.map((team) => ({ ...team, id: team.teamId }));
  state.chatMessages = frame.chat.items.map(adoptChatMessage);          // §8
  state.myWords = frame.words.words;

  state.room = {
    ...state.room,
    ...roomSummaryToRoomFields(frame.snapshot.room),                    // §2, поимённо
    ...matchStateToRoomFields(frame.match.match, state.room),           // §4, дополненный
    wordCount: frame.words.total,                                       // §7
    replacementRecordings: clipsToRecordings(frame.match.clips),        // §6
  };
}
```

Тот же `matchStateToRoomFields` + сборка боезапаса применяются к ответу
`GET /api/v2/game/{roomId}` и к полю `match` ответов операций хода — у них
одна форма с `frame.match`.
