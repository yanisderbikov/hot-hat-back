package ru.hothat.game.store;

import ru.hothat.game.domain.GuessedWord;
import ru.hothat.game.domain.LastTurn;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.ReplacementClip;
import ru.hothat.game.domain.SabotageEvent;
import ru.hothat.game.domain.SabotageLocks;
import ru.hothat.game.domain.TechnicalTermination;
import ru.hothat.game.domain.WeaponRegistry;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.util.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Перевод между строкой комнаты и состоянием партии.
 *
 * <p>Единственное место, где движок встречается с хранением. Смысл в том, что
 * форма хранения меняется отдельно от правил: сегодня партия лежит полями
 * строки {@code room} и её jsonb-колонками, завтра — своими таблицами
 * {@code match}, и правилам об этом знать незачем.
 *
 * <p>Чтение защитное: значения приезжают из свободных jsonb-структур, которые
 * писала ещё прошлая версия клиента, и незнакомая форма не должна ронять
 * партию.
 */
final class MatchMapper {

    private MatchMapper() {
    }

    // ───────────────────────── чтение ─────────────────────────

    static MatchState toState(Room room) {
        MatchState state = new MatchState(room.getId(),
                "sabotage".equals(room.getGameMode()),
                Boolean.TRUE.equals(room.getIsTestRoom()),
                Boolean.TRUE.equals(room.getRanked()),
                testBots(room),
                room.getTurnDuration() == null ? 60 : room.getTurnDuration());

        state.setPhase(MatchPhase.of(room.getPhase()));
        state.setGameNumber(room.getGameNumber() == null ? 0 : room.getGameNumber());
        state.setTestOwnerExplainerGameNumber(Math.max(0, room.getTestOwnerExplainerGameNumber() == null
                ? 0 : room.getTestOwnerExplainerGameNumber()));
        state.setTestOwnerExplainerTurnNumber(Math.max(0, room.getTestOwnerExplainerTurnNumber() == null
                ? 0 : room.getTestOwnerExplainerTurnNumber()));
        state.setTeamOrder(new ArrayList<>(room.getTeamOrder() == null ? List.of() : room.getTeamOrder()));
        state.setRosters(rosters(room));
        state.setPlayerNames(names(room));
        state.setCurrentTeamIndex(room.getCurrentTeamIndex() == null ? 0 : room.getCurrentTeamIndex());
        state.setCurrentTeamId(room.getCurrentTeamId());

        state.setTurnId(room.getTurnId());
        state.setExplainerUid(room.getExplainerUid());
        state.setExplainerName(room.getExplainerName());
        state.setGuesserUid(room.getGuesserUid());
        state.setGuesserName(room.getGuesserName());
        state.setTurnStartedAtMs(room.getTurnStartedAt() == null ? null : room.getTurnStartedAt().toEpochMilli());
        state.setTurnDurationSeconds(room.getTurnDurationSeconds());
        state.setLegacyTurnEndsAt(room.getTurnEndsAt() == null ? 0 : room.getTurnEndsAt());
        state.setCurrentTurnScore(room.getCurrentTurnScore() == null ? 0 : room.getCurrentTurnScore());
        state.setGuessedWords(words(room.getTurnGuessedWords()));

        state.setBag(new ArrayList<>(room.getBag() == null ? List.of() : room.getBag()));
        state.setCurrentWord(room.getCurrentWord());
        state.setWordsLeft(room.getWordsLeft() == null ? 0 : room.getWordsLeft());
        state.setLastGuessedWord(room.getLastGuessedWord());
        state.setLastSkippedWord(room.getLastSkippedWord());
        state.setLastActionType(room.getLastActionType());
        state.setLastActionWord(room.getLastActionWord());
        state.setLastActionAtMs(room.getLastActionAtMs() == null ? 0 : room.getLastActionAtMs());

        state.setAppealEndsAt(room.getAppealEndsAt() == null ? 0 : room.getAppealEndsAt());
        state.setAppealVotes(votes(room.getAppealVotes()));
        state.setLastTurn(lastTurn(room.getLastTurn()));

        state.setPaused(Boolean.TRUE.equals(room.getGamePaused()));
        state.setHostPaused(Boolean.TRUE.equals(room.getHostPaused()));
        state.setPauseReason(room.getPauseReason());
        state.setPauseMissingUids(new ArrayList<>(room.getPauseMissingUids() == null ? List.of() : room.getPauseMissingUids()));
        state.setPauseMissingNames(new ArrayList<>(room.getPauseMissingNames() == null ? List.of() : room.getPauseMissingNames()));
        state.setPauseStartedAtMs(room.getPauseStartedAtMs() == null ? 0 : room.getPauseStartedAtMs());
        state.setPausedTurnRemainingMs(room.getPausedTurnRemainingMs() == null ? 0 : room.getPausedTurnRemainingMs());
        state.setPausedAppealRemainingMs(room.getPausedAppealRemainingMs() == null ? 0 : room.getPausedAppealRemainingMs());
        state.setTermination(termination(room.getTechnicalTermination()));

        state.setLocks(locks(room.getSabotageLocks()));
        state.setClips(clips(room.getReplacementRecordings()));
        state.setLastEvent(event(room.getSabotageEvent()));
        state.setRecentEvents(events(room.getSabotageEventsRecent()));
        state.setSpecialProgressByTeam(counters(room.getSpecialRewardProgressByTeam()));
        state.setSpecialCursorByTeam(counters(room.getSpecialRewardCursorByTeam()));
        return state;
    }

    /**
     * Игрок партии из строки места.
     *
     * <p>У тест-бота боезапас и перезарядка берутся не из его строки, а из
     * {@code test_bot_runtime} комнаты: строку бот получает при рассадке и
     * больше не трогает, а стреляет им прежний движок ботов, который ведёт
     * счёт зарядов в этой карте. Без наложения плитка бота показывала бы
     * стартовый боезапас всю партию. Обойма мемов бота остаётся из строки:
     * прогон её не переписывает, а движку партии от бота нужен только факт
     * «пять заряжено».
     */
    static MatchPlayer toPlayer(RoomPlayer player, Room room) {
        MatchPlayer match = new MatchPlayer(player.getUid(), player.getName(), player.getTeamId(),
                Boolean.TRUE.equals(player.getIsTestBot()));
        match.setArsenal(WeaponRegistry.normalizeArsenal(intMap(player.getArsenal())));
        match.setLoadout(copy(player.getMemeLoadout()));
        match.setAvailable(copy(player.getMemeAvailableIds()));
        match.setReserve(copy(player.getMemeReserveIds()));
        match.setRecycle(copy(player.getMemeRecycleQueue()));
        match.setUsedMemeIds(copy(player.getUsedMemeIds()));
        match.setCycleCursor(player.getMemeCycleCursor() == null ? 0 : player.getMemeCycleCursor());
        match.setSabotageCooldownUntil(player.getSabotageCooldownUntil() == null ? 0 : player.getSabotageCooldownUntil());
        match.setLastSeenAt(player.getLastSeenAt() == null ? 0 : player.getLastSeenAt());
        if (match.isTestBot()) {
            Map<String, Object> runtime = Json.map(Json.map(room.getTestBotRuntime()).get(player.getUid()));
            if (runtime.containsKey("arsenal")) {
                match.setArsenal(WeaponRegistry.normalizeArsenal(intMap(Json.map(runtime.get("arsenal")))));
            }
            if (runtime.containsKey("sabotageCooldownUntil")) {
                match.setSabotageCooldownUntil(Math.max(0, Json.num(runtime.get("sabotageCooldownUntil"))));
            }
        }
        return match;
    }

    // ───────────────────────── запись ─────────────────────────

    static void apply(MatchState state, Room room) {
        room.setPhase(state.getPhase().code());
        room.setGameNumber(state.getGameNumber());
        room.setTeamOrder(new ArrayList<>(state.getTeamOrder()));
        room.setTeamRosters(new LinkedHashMap<>(state.getRosters()));
        room.setGamePlayerNamesByUid(new LinkedHashMap<>(state.getPlayerNames()));
        room.setCurrentTeamIndex(state.getCurrentTeamIndex());
        room.setCurrentTeamId(state.getCurrentTeamId());

        room.setTurnId(state.getTurnId());
        room.setExplainerUid(state.getExplainerUid());
        room.setExplainerName(state.getExplainerName());
        room.setGuesserUid(state.getGuesserUid());
        room.setGuesserName(state.getGuesserName());
        room.setTurnStartedAt(state.getTurnStartedAtMs() == null ? null : Instant.ofEpochMilli(state.getTurnStartedAtMs()));
        room.setTurnDurationSeconds(state.getTurnDurationSeconds());
        room.setTurnEndsAt(state.getLegacyTurnEndsAt());
        room.setCurrentTurnScore(state.getCurrentTurnScore());
        room.setTurnGuessedWords(wordsAsJson(state.getGuessedWords()));

        room.setBag(new ArrayList<>(state.getBag()));
        room.setCurrentWord(state.getCurrentWord());
        room.setWordsLeft(state.getWordsLeft());
        room.setLastGuessedWord(state.getLastGuessedWord());
        room.setLastSkippedWord(state.getLastSkippedWord());
        room.setLastActionType(state.getLastActionType());
        room.setLastActionWord(state.getLastActionWord());
        room.setLastActionAtMs(state.getLastActionAtMs());

        room.setAppealEndsAt(state.getAppealEndsAt());
        room.setAppealVotes(votesAsJson(state.getAppealVotes()));
        room.setLastTurn(lastTurnAsJson(state.getLastTurn()));

        room.setGamePaused(state.isPaused());
        room.setHostPaused(state.isHostPaused());
        room.setPauseReason(state.getPauseReason());
        room.setPauseMissingUids(new ArrayList<>(state.getPauseMissingUids()));
        room.setPauseMissingNames(new ArrayList<>(state.getPauseMissingNames()));
        room.setPauseStartedAtMs(state.getPauseStartedAtMs());
        room.setPausedTurnRemainingMs(state.getPausedTurnRemainingMs());
        room.setPausedAppealRemainingMs(state.getPausedAppealRemainingMs());
        room.setTechnicalTermination(terminationAsJson(state.getTermination()));

        room.setSabotageLocks(locksAsJson(state.getLocks()));
        room.setReplacementRecordings(clipsAsJson(state.getClips()));
        room.setSabotageEvent(eventAsJson(state.getLastEvent()));
        room.setSabotageEventsRecent(new ArrayList<>(state.getRecentEvents().stream()
                .map(MatchMapper::eventAsJson).map(Object.class::cast).toList()));
        room.setSpecialRewardProgressByTeam(new LinkedHashMap<>(state.getSpecialProgressByTeam()));
        room.setSpecialRewardCursorByTeam(new LinkedHashMap<>(state.getSpecialCursorByTeam()));
        room.setLastActivityAt(Math.max(room.getLastActivityAt() == null ? 0 : room.getLastActivityAt(),
                state.getLastActionAtMs()));
        room.touch();
    }

    static void apply(MatchPlayer match, RoomPlayer player) {
        player.setName(match.getName());
        player.setTeamId(match.getTeamId());
        player.setArsenal(new LinkedHashMap<>(match.getArsenal()));
        player.setMemeLoadout(new ArrayList<>(match.getLoadout()));
        player.setMemeAvailableIds(new ArrayList<>(match.getAvailable()));
        player.setMemeReserveIds(new ArrayList<>(match.getReserve()));
        player.setMemeRecycleQueue(new ArrayList<>(match.getRecycle()));
        player.setUsedMemeIds(new ArrayList<>(match.getUsedMemeIds()));
        player.setMemeCycleCursor(match.getCycleCursor());
        player.setSabotageCooldownUntil(match.getSabotageCooldownUntil());
        player.setLastSeenAt(match.getLastSeenAt());
        player.setUpdatedAt(Instant.now());
    }

    // ───────────────────────── мелочи ─────────────────────────

    private static java.util.Set<String> testBots(Room room) {
        if (!Boolean.TRUE.equals(room.getIsTestRoom()) || room.getTestBotIds() == null) {
            return java.util.Set.of();
        }
        return new java.util.LinkedHashSet<>(room.getTestBotIds());
    }

    private static Map<String, List<String>> rosters(Room room) {
        Map<String, List<String>> rosters = new LinkedHashMap<>();
        Json.map(room.getTeamRosters()).forEach((teamId, value) ->
                rosters.put(teamId, new ArrayList<>(Json.uniqueStrings(value))));
        return rosters;
    }

    private static Map<String, String> names(Room room) {
        Map<String, String> names = new LinkedHashMap<>();
        Json.map(room.getGamePlayerNamesByUid()).forEach((uid, value) -> names.put(uid, Json.str(value, 40)));
        return names;
    }

    private static List<GuessedWord> words(List<Object> stored) {
        List<GuessedWord> words = new ArrayList<>();
        for (Map<String, Object> item : Json.maps(stored)) {
            String id = Json.str(item.get("id"));
            if (!id.isEmpty()) {
                words.add(new GuessedWord(id, Json.str(item.get("word"), 80), Json.bool(item.get("skipped"))));
            }
        }
        return words;
    }

    private static List<Object> wordsAsJson(List<GuessedWord> words) {
        List<Object> stored = new ArrayList<>();
        for (GuessedWord word : words) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", word.id());
            item.put("word", word.word());
            if (word.skipped()) {
                // Пропуски помечены явно: их не видит ни счёт, ни апелляция,
                // но по ним узнаётся повтор запроса при потере связи.
                item.put("skipped", true);
            }
            stored.add(item);
        }
        return stored;
    }

    private static Map<String, List<String>> votes(Map<String, Object> stored) {
        Map<String, List<String>> votes = new LinkedHashMap<>();
        Json.map(stored).forEach((uid, value) -> votes.put(uid, new ArrayList<>(Json.strings(value))));
        return votes;
    }

    private static Map<String, Object> votesAsJson(Map<String, List<String>> votes) {
        return new LinkedHashMap<>(votes);
    }

    private static LastTurn lastTurn(Map<String, Object> stored) {
        Map<String, Object> item = Json.map(stored);
        if (item.isEmpty()) {
            return null;
        }
        List<GuessedWord> guessed = new ArrayList<>();
        for (Map<String, Object> word : Json.maps(item.get("guessedWords"))) {
            guessed.add(new GuessedWord(Json.str(word.get("id")), Json.str(word.get("word"), 80), false));
        }
        Long finalizedAt = item.containsKey("finalizedAtMs") ? Json.num(item.get("finalizedAtMs")) : null;
        Integer finalScore = item.containsKey("finalScore") ? (int) Json.num(item.get("finalScore")) : null;
        return new LastTurn(Json.str(item.get("turnId")), Json.str(item.get("teamId")),
                (int) Json.num(item.get("score")), guessed,
                Json.str(item.get("explainerUid")), Json.str(item.get("guesserUid")),
                finalScore,
                item.containsKey("invalidWordIds") ? Json.strings(item.get("invalidWordIds")) : null,
                item.containsKey("rewards") ? intMap(Json.map(item.get("rewards"))) : null,
                item.containsKey("specialRewardsByUid") ? specials(item.get("specialRewardsByUid")) : null,
                finalizedAt);
    }

    private static Map<String, Object> lastTurnAsJson(LastTurn lastTurn) {
        if (lastTurn == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("turnId", lastTurn.turnId());
        item.put("teamId", lastTurn.teamId());
        item.put("score", lastTurn.score());
        item.put("guessedWords", wordsAsJson(lastTurn.guessedWords()));
        item.put("explainerUid", lastTurn.explainerUid());
        item.put("guesserUid", lastTurn.guesserUid());
        if (lastTurn.settled()) {
            item.put("finalScore", lastTurn.finalScore());
            item.put("invalidWordIds", lastTurn.invalidWordIds());
            item.put("rewards", lastTurn.rewards());
            item.put("specialRewardsByUid", lastTurn.specialRewardsByUid());
            item.put("finalizedAtMs", lastTurn.finalizedAtMs());
        }
        return item;
    }

    private static SabotageLocks locks(Map<String, Object> stored) {
        Map<String, Object> raw = Json.map(stored);
        SabotageLocks locks = new SabotageLocks();
        locks.setVideoUntil(Json.num(raw.get("videoUntil")));
        locks.setVoiceUntil(Json.num(raw.get("voiceUntil")));
        locks.setCrocodileUntil(Json.num(raw.get("crocodileUntil")));
        locks.setOverlayUntil(Json.num(raw.get("overlayUntil")));
        locks.setReplacementUntil(Json.num(raw.get("replacementUntil")));
        locks.setReplacementTurnId(Json.str(raw.get("replacementTurnId")));
        Json.map(raw.get("replacementRecordingByAttacker")).forEach((uid, value) -> {
            long until = Json.num(value);
            if (!uid.isBlank() && until > 0) {
                locks.startRecording(uid, until);
            }
        });
        return locks;
    }

    private static Map<String, Object> locksAsJson(SabotageLocks locks) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("videoUntil", locks.videoUntil());
        raw.put("voiceUntil", locks.voiceUntil());
        raw.put("crocodileUntil", locks.crocodileUntil());
        raw.put("overlayUntil", locks.overlayUntil());
        raw.put("replacementUntil", locks.replacementUntil());
        raw.put("replacementTurnId", locks.replacementTurnId());
        // Поле осталось от общей блокировки съёмки: клиент прошлой версии его
        // читает, и без него он посчитал бы съёмку разрешённой всем сразу.
        raw.put("replacementRecordingUntil", 0L);
        raw.put("replacementRecordingByAttacker", new LinkedHashMap<>(locks.replacementRecordingByAttacker()));
        return raw;
    }

    private static Map<String, ReplacementClip> clips(Map<String, Object> stored) {
        Map<String, ReplacementClip> clips = new LinkedHashMap<>();
        Json.map(stored).forEach((clipId, value) -> {
            Map<String, Object> item = Json.map(value);
            if (item.isEmpty()) {
                return;
            }
            Long readyAt = Json.bool(item.get("ready")) ? Json.num(item.get("readyAtMs")) : null;
            clips.put(clipId, new ReplacementClip(clipId, Json.str(item.get("attackerUid")),
                    Json.str(item.get("targetUid")), Json.str(item.get("recordedTurnId")),
                    (int) Json.num(item.get("gameNumber")), Json.num(item.get("createdAtMs")), readyAt));
        });
        return clips;
    }

    private static Map<String, Object> clipsAsJson(Map<String, ReplacementClip> clips) {
        Map<String, Object> stored = new LinkedHashMap<>();
        clips.forEach((clipId, clip) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", clip.id());
            item.put("attackerUid", clip.attackerUid());
            item.put("targetUid", clip.targetUid());
            item.put("recordedTurnId", clip.recordedTurnId());
            item.put("gameNumber", clip.gameNumber());
            item.put("createdAtMs", clip.createdAtMs());
            if (clip.ready()) {
                item.put("ready", true);
                item.put("readyAtMs", clip.readyAtMs());
            }
            stored.put(clipId, item);
        });
        return stored;
    }

    static SabotageEvent event(Map<String, Object> stored) {
        Map<String, Object> item = Json.map(stored);
        if (item.isEmpty()) {
            return null;
        }
        return new SabotageEvent(Json.str(item.get("id")), Json.str(item.get("type")),
                blank(item.get("memeId")), blank(item.get("clipId")), blank(item.get("recordedTurnId")),
                Json.str(item.get("attackerUid")), Json.str(item.get("attackerName"), 40),
                blank(item.get("targetUid")), Json.num(item.get("createdAtMs")), Json.num(item.get("durationMs")),
                (int) Json.num(item.get("gameNumber")),
                item.containsKey("x") ? Json.dbl(item.get("x"), 0.5) : null,
                item.containsKey("y") ? Json.dbl(item.get("y"), 0.5) : null,
                blank(item.get("memeTitle")), blank(item.get("memeSrc")), blank(item.get("memePoster")),
                blank(item.get("memeMediaPath")), blank(item.get("memePosterPath")),
                blank(item.get("memeStorageProvider")), blank(item.get("text")), blank(item.get("voiceId")));
    }

    static Map<String, Object> eventAsJson(SabotageEvent event) {
        if (event == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", event.id());
        item.put("type", event.type());
        item.put("memeId", event.memeId());
        item.put("clipId", event.clipId());
        item.put("recordedTurnId", event.recordedTurnId());
        item.put("attackerUid", event.attackerUid());
        item.put("attackerName", event.attackerName());
        item.put("targetUid", event.targetUid());
        item.put("createdAtMs", event.createdAtMs());
        item.put("durationMs", event.durationMs());
        item.put("gameNumber", event.gameNumber());
        if (event.x() != null) {
            item.put("x", event.x());
            item.put("y", event.y());
        }
        if (event.memeId() != null) {
            item.put("memeTitle", event.memeTitle());
            item.put("memeSrc", event.memeSrc());
            item.put("memePoster", event.memePoster());
            item.put("memeMediaPath", event.memeMediaPath());
            item.put("memePosterPath", event.memePosterPath());
            item.put("memeStorageProvider", event.memeStorageProvider());
        }
        // Реплика облака есть только у ботов: обычный выстрел этих ключей
        // в JSON не получает, чтобы его запись не менялась.
        if (event.text() != null) {
            item.put("text", event.text());
        }
        if (event.voiceId() != null) {
            item.put("voiceId", event.voiceId());
        }
        return item;
    }

    private static List<SabotageEvent> events(List<Object> stored) {
        List<SabotageEvent> events = new ArrayList<>();
        for (Map<String, Object> item : Json.maps(stored)) {
            SabotageEvent event = event(item);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    private static TechnicalTermination termination(Map<String, Object> stored) {
        Map<String, Object> item = Json.map(stored);
        if (item.isEmpty()) {
            return null;
        }
        return new TechnicalTermination(Json.str(item.get("type")), Json.strings(item.get("missingUids")),
                Json.strings(item.get("missingNames")), Json.bool(item.get("noPenalty")),
                Json.num(item.get("endedAtMs")));
    }

    private static Map<String, Object> terminationAsJson(TechnicalTermination termination) {
        if (termination == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", termination.type());
        item.put("missingUids", termination.missingUids());
        item.put("missingNames", termination.missingNames());
        item.put("noPenalty", termination.noPenalty());
        item.put("endedAtMs", termination.endedAtMs());
        return item;
    }

    private static Map<String, Map<String, Integer>> specials(Object stored) {
        Map<String, Map<String, Integer>> byUid = new LinkedHashMap<>();
        Json.map(stored).forEach((uid, value) -> byUid.put(uid, intMap(Json.map(value))));
        return byUid;
    }

    private static Map<String, Integer> counters(Map<String, Object> stored) {
        return intMap(Json.map(stored));
    }

    private static Map<String, Integer> intMap(Map<String, Object> stored) {
        Map<String, Integer> counters = new LinkedHashMap<>();
        Json.map(stored).forEach((key, value) -> counters.put(key, (int) Json.num(value)));
        return counters;
    }

    private static String blank(Object value) {
        String text = Json.str(value);
        return text.isEmpty() ? null : text;
    }

    private static List<String> copy(List<String> values) {
        return new ArrayList<>(values == null ? List.of() : values);
    }
}
