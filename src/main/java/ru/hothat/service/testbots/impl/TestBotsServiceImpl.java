package ru.hothat.service.testbots.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.spi.MemeLibraryPort;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.model.room.RoomWordSubmission;
import ru.hothat.repository.GetterMedia;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.service.game.GameRules;
import ru.hothat.service.game.GameService;
import ru.hothat.service.livekit.LiveKitService;
import ru.hothat.service.testbots.TestBotsService;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;
import ru.hothat.util.Shuffle;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestBotsServiceImpl implements TestBotsService {

    private static final List<String> FALLBACK_LOADOUT = List.of("builtin-bmw-drugoy-ne-znayu");
    private static final Map<String, Integer> BOT_ARSENAL = Map.of("meme", 4, "tomato", 7, "crocodile", 2);
    private static final int TEST_THOUGHT_MAX_CHARS = 80;
    private static final int BOT_FART_CHANCE = 18;
    private static final int BOT_SHOT_MIN_MS = 9000;
    private static final int BOT_SHOT_MAX_MS = 20000;
    private static final int BOT_BURST_CHANCE = 21;
    private static final long TEST_SPECIAL_EFFECT_MS = 30000;
    private static final List<String> TEST_SPECIAL_EFFECT_TYPES =
            List.of("mask_gaddafi", "mask_kit_penot", "voice_bogdan", "voice_prokurish");
    private static final int BOT_CHAT_MIN_MS = 18000;
    private static final int BOT_CHAT_MAX_MS = 42000;

    private static final List<String> BOT_CHAT_LINES = List.of("ахаха 😄", "погнали", "ещё!", "норм 😄",
            "кто следующий?", "вот это игра", "🍅 приготовил", "почти одновременно 😄");
    private static final List<String> BOT_THOUGHT_LINES = List.of(
            "Я точно знаю это слово.",
            "Главное сейчас не запутаться.",
            "Кажется, меня специально сбивают.",
            "Почему все так подозрительно молчат?",
            "Сейчас угадают, я уверен.",
            "Я вообще-то всё контролирую.",
            "Надо было объяснять проще.",
            "Только бы не прилетел помидор.");
    private static final List<String> WORDS = List.of("трамвай", "почтовый ящик", "первая любовь",
            "случайная встреча", "ковёр", "футбол", "солёный огурец", "путешествие", "сосед", "кофемолка",
            "пингвин", "чемодан", "гроза", "музей", "велосипед", "космонавт", "будильник", "снежный человек",
            "морской пират", "волшебная палочка", "детектив", "торт", "пылесос", "остров", "дирижёр",
            "фонарик", "горячая шляпа", "смешной кот", "секретный агент", "водопад");

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    /** Библиотека мемов переехала в v2: её отдаёт область media. */
    private final MemeLibraryPort memeLibrary;
    private final LiveKitService liveKitService;
    private final GameService gameService;

    private static int delay(int min, int max) {
        return Ids.randomInt(min, max + 1);
    }

    private static int delay() {
        return delay(4000, 9000);
    }

    private static String botDisplayName(String botId, int fallbackIndex) {
        int dash = botId == null ? -1 : botId.lastIndexOf('-');
        if (dash >= 0 && dash < botId.length() - 1) {
            try {
                return "Тест-бот " + Integer.parseInt(botId.substring(dash + 1));
            } catch (NumberFormatException ignored) {
                // Идентификатор без числового хвоста — используем порядковый номер.
            }
        }
        return "Тест-бот " + (fallbackIndex + 1);
    }

    private Room requireTestRoom(HotHatUser user, String roomId) {
        Room room = getterRoom.getById(roomId).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        String owner = room.getTestOwnerUid() != null ? room.getTestOwnerUid() : Json.str(room.getCreatedBy());
        if (!Boolean.TRUE.equals(room.getIsTestRoom()) || !user.uid().equals(owner)) {
            throw ApiException.of("TEST_ROOM_ONLY", 403);
        }
        return room;
    }

    // ───────────────────────── создание комнаты ─────────────────────────

    /** Каталог мемов для ботов: встроенный ролик плюс то, что уже загружено. */
    private List<Map<String, Object>> libraryMemes(String publicOrigin) {
        Map<String, Object> builtin = new LinkedHashMap<>();
        builtin.put("id", FALLBACK_LOADOUT.get(0));
        builtin.put("title", "BMW — другой не знаю");
        builtin.put("durationMs", 5000);
        builtin.put("src", publicOrigin + "/assets/memes/bmw-drugoy-ne-znayu.mp4");
        builtin.put("poster", publicOrigin + "/assets/memes/bmw-drugoy-ne-znayu.webp");

        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        merged.put(FALLBACK_LOADOUT.get(0), builtin);
        try {
            // Каталог спрашивается у области media через порт: своя таблица
            // мемов у ботов кончилась вместе с переездом, и читать meme_library
            // теперь значило бы вооружать их только тем, что выложено до него.
            for (MemeLibraryPort.PlayableMeme meme : memeLibrary.catalog(120)) {
                String id = Json.str(meme.memeId()).trim();
                String src = Json.str(meme.videoUrl());
                if (id.isEmpty() || src.isEmpty()) {
                    // Мем без файла в хранилище боту не годится: показывать
                    // нечего, а место в обойме он бы занял.
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("title", Json.str(meme.title() == null ? "Мем" : meme.title(), 120));
                row.put("durationMs", Math.max(400, Math.min(10000,
                        meme.durationMs() <= 0 ? 3200 : meme.durationMs())));
                row.put("src", src);
                row.put("poster", Json.str(meme.posterUrl()));
                row.put("mediaPath", Json.str(meme.videoKey()));
                row.put("posterPath", Json.str(meme.posterKey()));
                row.put("storageProvider", meme.videoKey() == null ? "" : "s3-compatible");
                merged.put(id, row);
            }
        } catch (RuntimeException e) {
            log.warn("Каталог мемов недоступен, боты получат только встроенный: {}", e.getMessage());
        }
        return Shuffle.of(merged.values());
    }

    @Override
    @Transactional
    public Map<String, Object> setup(HotHatUser user, String roomId) {
        Room room = getterRoom.getById(roomId).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        if (!user.uid().equals(room.getCreatedBy())) {
            throw ApiException.of("HOST_ONLY", 403);
        }
        if (!"setup".equals(room.getPhase())) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        int maxPlayers = Set.of(4, 6, 8, 10).contains(room.getMaxPlayers()) ? room.getMaxPlayers() : 4;
        String suffix = roomId.substring(Math.max(0, roomId.length() - 6));
        List<String> botIds = new ArrayList<>();
        for (int i = 1; i < maxPlayers; i++) {
            botIds.add("testbot-" + suffix + "-" + i);
        }
        List<String> teamOrder = new ArrayList<>();
        for (int i = 1; i <= maxPlayers / 2; i++) {
            teamOrder.add("test-team-" + i);
        }
        List<String> participants = new ArrayList<>();
        participants.add(user.uid());
        participants.addAll(botIds);
        long now = System.currentTimeMillis();

        List<Map<String, Object>> catalog = libraryMemes("https://hot-hat.web.app");
        List<String> memePool = catalog.stream().map(item -> Json.str(item.get("id"))).toList();
        Map<String, Map<String, Object>> metaCatalog = new LinkedHashMap<>();
        catalog.forEach(item -> metaCatalog.put(Json.str(item.get("id")), item));

        Map<String, Object> runtime = new LinkedHashMap<>();
        for (int index = 0; index < botIds.size(); index++) {
            String botId = botIds.get(index);
            List<String> loadout = Shuffle.of(memePool)
                    .subList(0, Math.max(1, Math.min(5, memePool.size())));
            Map<String, Object> metaById = new LinkedHashMap<>();
            for (String memeId : loadout) {
                metaById.put(memeId, metaCatalog.getOrDefault(memeId,
                        new LinkedHashMap<>(Map.of("id", memeId, "title", "Мем",
                                "durationMs", 3200, "src", "", "poster", ""))));
            }
            int unlocked = Math.min(4, loadout.size());
            List<String> available = new ArrayList<>(loadout.subList(0, unlocked));
            List<String> reserve = new ArrayList<>(loadout.subList(unlocked, loadout.size()));
            String name = "Тест-бот " + (index + 1);

            saverRoom.savePlayer(RoomPlayer.builder()
                    .roomId(roomId)
                    .uid(botId)
                    .name(name)
                    .isTestBot(true)
                    .testBotIndex(index + 1)
                    .teamId(teamOrder.get((index + 1) / 2))
                    .joinedAt(Instant.now())
                    .lastSeenAt(now)
                    .mediaRevision(now)
                    .mediaReadyAt(now)
                    .cameraEnabled(true)
                    .microphoneEnabled(false)
                    .memeLoadout(new ArrayList<>(loadout))
                    .usedMemeIds(new ArrayList<>())
                    .memeAvailableIds(new ArrayList<>(available))
                    .memeReserveIds(new ArrayList<>(reserve))
                    .memeRecycleQueue(new ArrayList<>())
                    .memeCycleCursor(0)
                    .arsenal(new LinkedHashMap<>(BOT_ARSENAL))
                    .sabotageCooldownUntil(0L)
                    .build());

            Map<String, Object> botRuntime = new LinkedHashMap<>();
            botRuntime.put("name", name);
            botRuntime.put("memeLoadout", new ArrayList<>(loadout));
            botRuntime.put("memeMetaById", metaById);
            botRuntime.put("usedMemeIds", new ArrayList<>());
            botRuntime.put("memeAvailableIds", new ArrayList<>(available));
            botRuntime.put("memeReserveIds", new ArrayList<>(reserve));
            botRuntime.put("memeRecycleQueue", new ArrayList<>());
            botRuntime.put("memeCycleCursor", 0);
            botRuntime.put("arsenal", new LinkedHashMap<>(BOT_ARSENAL));
            botRuntime.put("sabotageCooldownUntil", 0);
            botRuntime.put("testBotLastShotAt", 0);
            runtime.put(botId, botRuntime);
        }

        getterRoom.getPlayer(roomId, user.uid()).ifPresent(player -> {
            player.setTeamId(teamOrder.get(0));
            saverRoom.savePlayer(player);
        });
        for (int index = 0; index < teamOrder.size(); index++) {
            String teamId = teamOrder.get(index);
            saverRoom.saveTeam(RoomTeam.builder()
                    .roomId(roomId)
                    .teamId(teamId)
                    .name("Тестовая команда " + (index + 1))
                    .order(index)
                    .memberUids(new ArrayList<>(participants.subList(index * 2,
                            Math.min(index * 2 + 2, participants.size()))))
                    .score(0)
                    .build());
        }
        saverRoom.saveWordSubmission(RoomWordSubmission.builder()
                .roomId(roomId)
                .submissionId("test-bots")
                .words(new ArrayList<>(WORDS))
                .count(WORDS.size())
                .isTestBotSubmission(true)
                .build());

        room.setIsTestRoom(true);
        room.setTestOwnerUid(user.uid());
        room.setTestBotIds(botIds);
        room.setTestBotRuntime(runtime);
        room.setTestBotNextActionAt(now + delay(3000, 5500));
        room.setTestBotNextSabotageAt(now + delay(BOT_SHOT_MIN_MS, BOT_SHOT_MAX_MS));
        room.setTestBotSpecialEffectUntil(0L);
        room.setTestBotSpecialEffectCursor(0);
        room.setTestBotVoiceEffectUntil(0L);
        room.setTestBotVoiceEffectCursor(0);
        room.setTestOwnerExplainerGameNumber(0);
        room.setTestOwnerExplainerTurnNumber(0);
        room.setTestBotNextChatAt(now + delay(12000, 26000));
        room.setIsPrivate(false);
        room.setTeamOrder(teamOrder);
        room.setMaxPlayers(maxPlayers);
        room.setMaxParticipants(maxPlayers);
        // Счётчик — сумма всех заявок, как его считает переходник заявок и
        // кадр канала (wordSubmissions.total): хозяин мог сдать свои слова до
        // вызова ботов, и одни только WORDS.size() стёрли бы их из счёта.
        room.setWordCount(getterRoom.getWordSubmissions(roomId).stream()
                .mapToInt(submission -> submission.getCount() == null ? 0 : submission.getCount())
                .sum());
        room.setWordRevision(room.getWordRevision() + 1);
        saverRoom.save(room);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", roomId);
        result.put("botIds", botIds);
        result.put("teamOrder", teamOrder);
        result.put("maxPlayers", maxPlayers);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> stop(HotHatUser user, String roomId) {
        Room room = getterRoom.getById(roomId).orElse(null);
        if (room == null) {
            return Map.of("roomId", roomId, "closed", true);
        }
        String owner = room.getTestOwnerUid() != null ? room.getTestOwnerUid() : Json.str(room.getCreatedBy());
        if (!Boolean.TRUE.equals(room.getIsTestRoom()) || !user.uid().equals(owner)) {
            throw ApiException.of("TEST_ROOM_ONLY", 403);
        }
        saverRoom.deletePlayer(roomId, user.uid());
        for (String botId : room.getTestBotIds()) {
            saverRoom.deletePlayer(roomId, botId);
        }
        for (String teamId : room.getTeamOrder()) {
            saverRoom.deleteTeam(roomId, teamId);
        }
        saverRoom.deleteWordSubmission(roomId, "test-bots");
        room.setPhase("closed");
        room.setClosedAt(Instant.now());
        room.setClosedBy(user.uid());
        room.setClosedReason("test_room_stopped");
        saverRoom.save(room);
        return Map.of("roomId", roomId, "closed", true);
    }

    // ───────────────────────── шаг раннера ─────────────────────────

    /**
     * Один тик: ведёт ход тестовой партии и, если пришло время, стреляет
     * диверсией и пишет в чат. Возвращает клиенту, через сколько будить снова.
     */
    @Override
    @Transactional
    public Map<String, Object> tick(HotHatUser user, String roomId) {
        Room room = requireTestRoom(user, roomId);
        long now = System.currentTimeMillis();

        Map<String, Object> outcome;
        if (Boolean.TRUE.equals(room.getGamePaused())) {
            outcome = step(room, "paused", 5000);
        } else if (List.of("setup", "finished", "closed").contains(room.getPhase())) {
            outcome = step(room, "idle", "setup".equals(room.getPhase()) ? 40000 : 30000);
        } else if ("appeal".equals(room.getPhase())) {
            long left = room.getAppealEndsAt() - now;
            if (left <= 0) {
                Map<String, Object> result = gameService.finalizeAppeal(user, roomId);
                boolean finished = Boolean.TRUE.equals(result.get("finished"));
                Map<String, Object> finalized = new LinkedHashMap<>();
                finalized.put("phase", finished ? "finished" : "turnIntro");
                finalized.put("action", "appeal_finalized");
                finalized.put("waitMs", finished ? 30000 : 2500);
                return finalized;
            }
            outcome = step(room, "waiting", Math.max(500, left));
        } else {
            outcome = advanceTurn(room, user, now);
        }

        if ("active".equals(Json.str(outcome.get("phase")))) {
            runBotSabotageAndChat(roomId, user, outcome);
        }
        outcome.remove("botIds");
        outcome.remove("nextSabotageAt");
        outcome.remove("nextChatAt");
        outcome.remove("gameMode");
        return outcome;
    }

    private Map<String, Object> step(Room room, String action, long waitMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phase", room.getPhase());
        result.put("action", action);
        result.put("waitMs", waitMs);
        result.put("botIds", new ArrayList<>(room.getTestBotIds()));
        result.put("nextSabotageAt", room.getTestBotNextSabotageAt());
        result.put("nextChatAt", room.getTestBotNextChatAt());
        result.put("gameMode", room.getGameMode());
        return result;
    }

    private Map<String, Object> advanceTurn(Room room, HotHatUser user, long now) {
        long due = room.getTestBotNextActionAt();
        if (due == 0) {
            long waitMs = delay();
            room.setTestBotNextActionAt(now + waitMs);
            saverRoom.save(room);
            return step(room, "waiting", waitMs);
        }
        if (now < due) {
            return step(room, "waiting", Math.max(500, due - now));
        }
        if ("turnIntro".equals(room.getPhase())) {
            return startTurn(room, user, now);
        }
        if ("active".equals(room.getPhase())) {
            return playActiveTurn(room, user, now);
        }
        return step(room, "idle", 5000);
    }

    private Map<String, Object> startTurn(Room room, HotHatUser user, long now) {
        List<String> roster = GameRules.rosterForTeam(room, room.getCurrentTeamId());
        Set<String> botSet = new HashSet<>(room.getTestBotIds());
        String ownerInRoster = roster.contains(user.uid()) ? user.uid() : null;
        String explainerUid = ownerInRoster != null ? ownerInRoster
                : roster.stream().filter(botSet::contains).findFirst()
                .orElse(roster.isEmpty() ? null : roster.get(0));
        String guesserUid = roster.stream().filter(uid -> !uid.equals(explainerUid)).findFirst()
                .orElse(roster.size() > 1 ? roster.get(1) : null);

        List<String> bag = new ArrayList<>(room.getBag());
        if (bag.isEmpty()) {
            room.setPhase("finished");
            room.setCurrentWord(null);
            room.setWordsLeft(0);
            saverRoom.save(room);
            return step(room, "finished", 30000);
        }
        String currentWord = Shuffle.take(bag);
        Map<String, Object> names = Json.map(room.getGamePlayerNamesByUid());
        int gameNumber = Math.max(0, room.getGameNumber());
        // Счётчик ходов владельца нужен, чтобы не бить его диверсиями в первых двух.
        boolean sameGame = room.getTestOwnerExplainerGameNumber() == gameNumber;
        int currentOwnerTurn = sameGame ? Math.max(0, room.getTestOwnerExplainerTurnNumber()) : 0;
        int nextOwnerTurn = ownerInRoster != null ? currentOwnerTurn + 1 : currentOwnerTurn;

        long waitMs = delay(4500, 9500);
        room.setPhase("active");
        room.setBag(bag);
        room.setCurrentWord(currentWord);
        room.setWordsLeft(bag.size() + 1);
        room.setExplainerUid(explainerUid);
        room.setExplainerName(Json.str(names.getOrDefault(explainerUid, "Тест-бот")));
        room.setGuesserUid(guesserUid);
        room.setGuesserName(Json.str(names.getOrDefault(guesserUid, "Тест-бот")));
        room.setCurrentTurnScore(0);
        room.setTurnStartedAt(Instant.ofEpochMilli(now));
        room.setTurnDurationSeconds((double) (room.getTurnDuration() == null ? 60 : room.getTurnDuration()));
        room.setTurnEndsAt(0L);
        room.setTurnId("test-turn-" + now);
        room.setTurnGuessedWords(new ArrayList<>());
        room.setAppealEndsAt(0L);
        room.setAppealVotes(new LinkedHashMap<>());
        room.setTestOwnerExplainerGameNumber(gameNumber);
        room.setTestOwnerExplainerTurnNumber(nextOwnerTurn);
        room.setTestBotNextActionAt(now + waitMs);
        saverRoom.save(room);
        return step(room, "turn_started", waitMs);
    }

    private Map<String, Object> playActiveTurn(Room room, HotHatUser user, long now) {
        long deadline = GameRules.currentTurnDeadline(room);
        if (deadline > 0 && now >= deadline) {
            applyAppealPatch(room, now);
            saverRoom.save(room);
            return step(room, "turn_ended", 10000);
        }
        // Кнопки «Угадали»/«Пропустить» принадлежат живому объясняющему: бот их не нажимает.
        if (user.uid().equals(Json.str(room.getExplainerUid()))) {
            return step(room, "owner_controls_words", 3000);
        }

        List<String> bag = new ArrayList<>(room.getBag());
        boolean guessed = Ids.randomInt(100) < 78;
        String currentWord = Json.str(room.getCurrentWord());
        if (guessed) {
            getterRoom.getTeam(room.getId(), Json.str(room.getCurrentTeamId())).ifPresent(team -> {
                team.setScore(team.getScore() + 1);
                saverRoom.saveTeam(team);
            });
            List<Object> guessedWords = new ArrayList<>(Json.list(room.getTurnGuessedWords()));
            guessedWords.add(new LinkedHashMap<>(Map.of(
                    "id", "test-guess-" + now, "word", Json.str(currentWord, 80))));
            int score = room.getCurrentTurnScore() + 1;
            room.setTurnGuessedWords(guessedWords);
            room.setCurrentTurnScore(score);
            room.setLastGuessedWord(currentWord);
            room.setLastActionType("guessed");
            room.setLastActionWord(currentWord);
            room.setLastActionAtMs(now);
            if (bag.isEmpty()) {
                room.setBag(bag);
                room.setCurrentWord(null);
                applyAppealPatch(room, now);
                saverRoom.save(room);
                return step(room, "guessed_last", 10000);
            }
            String nextWord = Shuffle.take(bag);
            long waitMs = delay(4500, 9500);
            room.setBag(bag);
            room.setCurrentWord(nextWord);
            room.setWordsLeft(bag.size() + 1);
            room.setTestBotNextActionAt(now + waitMs);
            saverRoom.save(room);
            return step(room, "guessed", waitMs);
        }

        long waitMs = delay(3500, 7500);
        if (!bag.isEmpty()) {
            String nextWord = Shuffle.take(bag);
            bag.add(currentWord);
            room.setBag(bag);
            room.setCurrentWord(nextWord);
            room.setWordsLeft(bag.size() + 1);
        } else {
            // Последнее слово и пропуск: слово остаётся, ход продолжается — как у людей.
            room.setWordsLeft(1);
        }
        room.setLastSkippedWord(currentWord);
        room.setLastActionType("skipped");
        room.setLastActionWord(currentWord);
        room.setLastActionAtMs(now);
        room.setTestBotNextActionAt(now + waitMs);
        saverRoom.save(room);
        return step(room, bag.isEmpty() ? "skipped_only_word" : "skipped", waitMs);
    }

    /** Переход в апелляцию: текущее слово возвращается в шляпу, счёт фиксируется. */
    private void applyAppealPatch(Room room, long now) {
        List<String> bag = new ArrayList<>(room.getBag());
        if (room.getCurrentWord() != null && !room.getCurrentWord().isBlank()) {
            bag.add(0, room.getCurrentWord());
        }
        List<Object> guessedWords = new ArrayList<>(Json.list(room.getTurnGuessedWords()));
        String lastGuessed = room.getLastGuessedWord();
        if ((lastGuessed == null || lastGuessed.isBlank()) && !guessedWords.isEmpty()) {
            lastGuessed = Json.str(Json.map(guessedWords.get(guessedWords.size() - 1)).get("word"));
        }
        Map<String, Object> lastTurn = new LinkedHashMap<>();
        lastTurn.put("turnId", room.getTurnId() == null ? "test-turn-" + now : room.getTurnId());
        lastTurn.put("teamId", room.getCurrentTeamId());
        lastTurn.put("score", room.getCurrentTurnScore());
        lastTurn.put("guessedWords", guessedWords);
        lastTurn.put("explainerUid", room.getExplainerUid());
        lastTurn.put("guesserUid", room.getGuesserUid());

        room.setPhase("appeal");
        room.setBag(Shuffle.of(bag));
        room.setCurrentWord(null);
        room.setWordsLeft(bag.size());
        room.setTurnStartedAt(null);
        room.setTurnDurationSeconds(null);
        room.setTurnEndsAt(0L);
        room.setLastGuessedWord(lastGuessed);
        room.setAppealEndsAt(now + 10000);
        room.setAppealVotes(new LinkedHashMap<>());
        room.setSabotageEvent(null);
        room.setSabotageCooldownUntil(0L);
        room.setLastTurn(lastTurn);
        room.setTestBotNextActionAt(now + 10500);
    }

    // ───────────────────────── выстрелы и чат ботов ─────────────────────────

    private void runBotSabotageAndChat(String roomId, HotHatUser user, Map<String, Object> outcome) {
        List<String> shots = new ArrayList<>();
        if ("sabotage".equals(Json.str(outcome.get("gameMode")))
                && System.currentTimeMillis() >= Json.num(outcome.get("nextSabotageAt"))) {
            Map<String, Object> first = botSabotage(roomId, user, false, "", false);
            if (first != null) {
                shots.add(Json.str(first.get("type")));
                broadcast(roomId, Json.map(first.get("event")));
                // Иногда боты стреляют залпом — так тестируется наложение эффектов.
                if (Ids.randomInt(100) < BOT_BURST_CHANCE) {
                    Map<String, Object> second = botSabotage(roomId, user, true,
                            Json.str(first.get("attackerUid")), true);
                    if (second != null) {
                        shots.add(Json.str(second.get("type")));
                        broadcast(roomId, Json.map(second.get("event")));
                    }
                }
            }
        }
        if (!shots.isEmpty()) {
            outcome.put("botShots", shots);
        }
        if (System.currentTimeMillis() >= Json.num(outcome.get("nextChatAt"))) {
            String hint = !shots.isEmpty() ? "shot" : Json.str(outcome.get("action"));
            if (botChat(roomId, hint, Json.strings(outcome.get("botIds")),
                    Json.num(outcome.get("nextChatAt")))) {
                outcome.put("botChat", true);
            }
        }
    }

    private void broadcast(String roomId, Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            return;
        }
        try {
            liveKitService.sendSabotage(roomId, event);
        } catch (RuntimeException e) {
            log.debug("Рассылка выстрела бота отложена: {}", e.getMessage());
        }
    }

    /** Приоритет оружия у ботов: чаще помидоры, реже мемы, совсем редко крокодил. */
    private String weightedWeapon(Map<String, Integer> arsenal, boolean burst) {
        int roll = Ids.randomInt(100);
        List<String> order = burst
                ? (roll < 86 ? List.of("tomato", "meme", "crocodile") : List.of("meme", "tomato", "crocodile"))
                : (roll < 76 ? List.of("tomato", "meme", "crocodile")
                : roll < 98 ? List.of("meme", "tomato", "crocodile") : List.of("crocodile", "tomato", "meme"));
        return order.stream().filter(type -> arsenal.getOrDefault(type, 0) > 0).findFirst().orElse(null);
    }

    private Map<String, Object> botSabotage(String roomId, HotHatUser user, boolean force,
                                            String excludeUid, boolean burst) {
        Room room = getterRoom.getById(roomId).orElse(null);
        if (room == null) {
            return null;
        }
        String owner = room.getTestOwnerUid() != null ? room.getTestOwnerUid() : Json.str(room.getCreatedBy());
        if (!Boolean.TRUE.equals(room.getIsTestRoom()) || !user.uid().equals(owner)) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (!"active".equals(room.getPhase()) || Boolean.TRUE.equals(room.getGamePaused())
                || !"sabotage".equals(room.getGameMode()) || Json.str(room.getExplainerUid()).isEmpty()) {
            return null;
        }
        if (!force && room.getTestBotNextSabotageAt() > now) {
            return null;
        }
        Set<String> activeRoster = new HashSet<>(GameRules.rosterForTeam(room, room.getCurrentTeamId()));
        List<String> ids = room.getTestBotIds().stream()
                .filter(id -> !id.isBlank() && !activeRoster.contains(id) && !id.equals(excludeUid)).toList();
        if (ids.isEmpty()) {
            room.setTestBotNextSabotageAt(now + delay(BOT_SHOT_MIN_MS, BOT_SHOT_MAX_MS));
            saverRoom.save(room);
            return null;
        }
        Map<String, Object> runtime = Json.map(room.getTestBotRuntime());
        boolean targetIsOwner = user.uid().equals(Json.str(room.getExplainerUid()));
        int ownerTurnGameNumber = Math.max(0, room.getTestOwnerExplainerGameNumber());
        int ownerTurnNumber = ownerTurnGameNumber == Math.max(0, room.getGameNumber())
                ? Math.max(0, room.getTestOwnerExplainerTurnNumber()) : 0;
        // Первые два хода владельца боты не трогают: он успевает освоиться.
        if (targetIsOwner && (ownerTurnNumber == 1 || ownerTurnNumber == 2)) {
            long deadline = GameRules.currentTurnDeadline(room);
            room.setTestBotNextSabotageAt(Math.max(now + 5000, deadline + 1500));
            saverRoom.save(room);
            return null;
        }

        long specialUntil = Math.max(room.getTestBotSpecialEffectUntil(), room.getTestBotVoiceEffectUntil());
        boolean specialReady = targetIsOwner && specialUntil <= now;
        // Из кандидатов выбираем половину самых давно стрелявших — очередь по кругу.
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> data = runtime.containsKey(id) ? Json.map(runtime.get(id)) : defaultBotRuntime(id);
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("id", id);
            candidate.put("data", data);
            candidate.put("last", Math.max(Json.num(data.get("testBotLastShotAt")), Json.num(data.get("lastShotAt"))));
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparingLong(c -> Json.num(c.get("last"))));
        List<Map<String, Object>> oldest = candidates.subList(0,
                Math.max(1, (int) Math.ceil(candidates.size() / 2.0)));
        Map<String, Object> attacker = oldest.get(Ids.randomInt(oldest.size()));
        Map<String, Object> attackerData = Json.map(attacker.get("data"));
        Map<String, Integer> attackerArsenal = botArsenal(attackerData.get("arsenal"));

        int specialCursor = Math.max(0, room.getTestBotSpecialEffectCursor());
        boolean fartRoll = !specialReady && Ids.randomInt(100) < BOT_FART_CHANCE;
        boolean thoughtRoll = !specialReady && !fartRoll && Ids.randomInt(100) < 30;
        String type = specialReady
                ? TEST_SPECIAL_EFFECT_TYPES.get(specialCursor % TEST_SPECIAL_EFFECT_TYPES.size())
                : (fartRoll ? "fart" : thoughtRoll ? "thought_cloud" : weightedWeapon(attackerArsenal, burst));
        if (type == null) {
            return null;
        }
        if ("fart".equals(type)) {
            // Пукнуть может любой бот, включая активную команду.
            List<String> fartIds = room.getTestBotIds().stream()
                    .filter(id -> !id.isBlank() && !id.equals(excludeUid)).toList();
            if (!fartIds.isEmpty()) {
                String fartId = fartIds.get(Ids.randomInt(fartIds.size()));
                attackerData = runtime.containsKey(fartId) ? Json.map(runtime.get(fartId)) : defaultBotRuntime(fartId);
                attacker = new LinkedHashMap<>(Map.of("id", fartId, "data", attackerData));
                attackerArsenal = botArsenal(attackerData.get("arsenal"));
            }
        }
        String attackerId = Json.str(attacker.get("id"));
        boolean isSpecial = TEST_SPECIAL_EFFECT_TYPES.contains(type);
        boolean isThought = "thought_cloud".equals(type);
        boolean isFart = "fart".equals(type);
        if (!isSpecial && !isThought && !isFart) {
            attackerArsenal.merge(type, -1, Integer::sum);
        }

        long turnDeadline = GameRules.currentTurnDeadline(room);
        long durationMs = isSpecial ? TEST_SPECIAL_EFFECT_MS
                : (isThought || isFart) ? 0
                : "tomato".equals(type) ? 2000
                : "crocodile".equals(type) ? Math.max(1200, turnDeadline - now)
                : 3200;

        List<String> memeChoices = Json.strings(attackerData.get("memeAvailableIds"));
        if (memeChoices.isEmpty()) {
            memeChoices = Json.strings(attackerData.get("memeLoadout"));
        }
        if (memeChoices.isEmpty()) {
            memeChoices = new ArrayList<>(FALLBACK_LOADOUT);
        }
        String memeId = "meme".equals(type) ? memeChoices.get(Ids.randomInt(memeChoices.size())) : null;
        Map<String, Object> memeMeta = memeId == null ? Map.of()
                : Json.map(Json.map(attackerData.get("memeMetaById")).get(memeId));

        long eventNow = System.currentTimeMillis();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "test-sab-" + Ids.hex(8));
        event.put("type", type);
        event.put("memeId", memeId);
        event.put("attackerUid", attackerId);
        event.put("attackerName", Json.str(attackerData.getOrDefault("name", botDisplayName(attackerId, 0)), 40));
        event.put("targetUid", Json.str(room.getExplainerUid()));
        event.put("createdAtMs", eventNow);
        event.put("durationMs", "meme".equals(type)
                ? Math.max(400, Math.min(10000, Json.num(memeMeta.get("durationMs"), 3200))) : durationMs);
        event.put("gameNumber", room.getGameNumber());
        if (isSpecial) {
            event.put("specialOrderVersion", 4);
        }
        if (isThought) {
            event.put("text", Json.str(BOT_THOUGHT_LINES.get(Ids.randomInt(BOT_THOUGHT_LINES.size())),
                    TEST_THOUGHT_MAX_CHARS));
            event.put("voiceId", String.valueOf(1 + Ids.randomInt(3)));
        }
        if ("meme".equals(type)) {
            event.put("memeTitle", Json.str(memeMeta.getOrDefault("title", "Мем"), 120));
            event.put("memeSrc", Json.str(memeMeta.get("src"), 2200));
            event.put("memePoster", Json.str(memeMeta.get("poster"), 2200));
            event.put("memeMediaPath", Json.str(memeMeta.get("mediaPath"), 500));
            event.put("memePosterPath", Json.str(memeMeta.get("posterPath"), 500));
            event.put("memeStorageProvider", Json.str(memeMeta.get("storageProvider"), 40));
        }

        attackerData.put("arsenal", new LinkedHashMap<>(attackerArsenal));
        attackerData.put("testBotLastShotAt", eventNow);
        attackerData.put("lastShotAt", eventNow);
        attackerData.put("sabotageCooldownUntil",
                ("tomato".equals(type) || isFart) ? 0 : eventNow + 25000);
        runtime.put(attackerId, attackerData);

        room.setTestBotRuntime(runtime);
        room.setSabotageEvent(event);
        room.setSabotageEventsRecent(GameRules.appendRecentSabotage(room, event));
        room.setTestBotNextSabotageAt(eventNow + delay(BOT_SHOT_MIN_MS, BOT_SHOT_MAX_MS));
        if (isSpecial) {
            room.setTestBotSpecialEffectUntil(eventNow + TEST_SPECIAL_EFFECT_MS);
            room.setTestBotSpecialEffectCursor(specialCursor + 1);
            // Старые поля продолжаем обновлять ради совместимости с прежними клиентами.
            room.setTestBotVoiceEffectUntil(eventNow + TEST_SPECIAL_EFFECT_MS);
            room.setTestBotVoiceEffectCursor(specialCursor + 1);
        }
        saverRoom.save(room);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", event);
        result.put("attackerUid", attackerId);
        result.put("type", type);
        return result;
    }

    private Map<String, Object> defaultBotRuntime(String botId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", botDisplayName(botId, 0));
        data.put("arsenal", new LinkedHashMap<>(BOT_ARSENAL));
        data.put("memeLoadout", new ArrayList<>(FALLBACK_LOADOUT));
        data.put("memeAvailableIds", new ArrayList<>(FALLBACK_LOADOUT));
        return data;
    }

    private static Map<String, Integer> botArsenal(Object value) {
        Map<String, Object> raw = Json.map(value);
        Map<String, Integer> arsenal = new LinkedHashMap<>();
        for (String key : List.of("meme", "tomato", "crocodile")) {
            arsenal.put(key, (int) Math.max(0, Json.num(raw.get(key))));
        }
        return arsenal;
    }

    private boolean botChat(String roomId, String hint, List<String> botIds, long nextChatAt) {
        long now = System.currentTimeMillis();
        if (nextChatAt > now || botIds.isEmpty()) {
            return false;
        }
        int pick = Ids.randomInt(botIds.size());
        String botId = botIds.get(pick);
        List<String> pool = switch (hint == null ? "" : hint) {
            case "guessed" -> List.of("есть! 😄", "угадали!", "хорош!", "вот это быстро");
            case "shot" -> List.of("🍅 летит!", "держи 😄", "мем пошёл", "КАДДАФИ включён 🕶️",
                    "БОГДАН пошёл 😄", "КИТ ПЕНОТ напал 🦝", "ПРОКУРЫШ включён 😄", "вот это залп");
            default -> hint != null && hint.startsWith("skipped")
                    ? List.of("сложно было", "дальше 😄", "ну это жёстко", "следующее!")
                    : BOT_CHAT_LINES;
        };
        Room room = getterRoom.getById(roomId).orElse(null);
        if (room == null) {
            return false;
        }
        saverRoom.saveChatMessage(RoomChatMessage.builder()
                .roomId(roomId)
                .id("test-chat-" + Ids.hex(8))
                .uid(botId)
                .name(botDisplayName(botId, pick))
                .role("player")
                .text(pool.get(Ids.randomInt(pool.size())))
                .isTestBot(true)
                .createdAtMs(now)
                .build());
        room.setTestBotNextChatAt(now + delay(BOT_CHAT_MIN_MS, BOT_CHAT_MAX_MS));
        saverRoom.save(room);
        return true;
    }

    /** Ручной «пук» владельца: событие без боезапаса и кулдауна. */
    @Override
    @Transactional
    public Map<String, Object> fireOwnerFart(HotHatUser user, String roomId, Map<String, Object> body) {
        Room room = getterRoom.getById(roomId).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        String owner = room.getTestOwnerUid() != null ? room.getTestOwnerUid() : Json.str(room.getCreatedBy());
        if (!Boolean.TRUE.equals(room.getIsTestRoom()) || !user.uid().equals(owner)) {
            throw ApiException.of("OWNER_ONLY", 403);
        }
        if (!"active".equals(room.getPhase()) || Boolean.TRUE.equals(room.getGamePaused())) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
        RoomPlayer player = getterRoom.getPlayer(roomId, user.uid()).orElse(null);
        String requestedId = Json.str(body.get("event_id")).trim();
        // Клиент может задать свой id, чтобы не проиграть один и тот же звук дважды.
        String id = requestedId.matches("^fart-[A-Za-z0-9_-]{8,220}$")
                ? requestedId
                : "fart-" + user.uid() + "-" + System.currentTimeMillis() + "-" + Ids.hex(4);
        long now = System.currentTimeMillis();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", id);
        event.put("type", "fart");
        event.put("attackerUid", user.uid());
        event.put("attackerName", Json.str(player != null && player.getName() != null
                ? player.getName() : (user.name() == null ? "Игрок" : user.name()), 40));
        event.put("targetUid", Json.str(room.getExplainerUid()));
        event.put("createdAtMs", now);
        event.put("durationMs", 0);
        event.put("gameNumber", room.getGameNumber());

        room.setSabotageEvent(event);
        room.setSabotageEventsRecent(GameRules.appendRecentSabotage(room, event));
        saverRoom.save(room);
        broadcast(roomId, event);
        return Map.of("event", event);
    }
}
