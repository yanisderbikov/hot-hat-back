package ru.hothat.service.game;

import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.util.Json;

import java.util.*;

/** Чистые правила партии, перенесённые из api/game.js. */
public final class GameRules {

    /** Стартовый боезапас на партию. */
    public static final Map<String, Integer> BASE_ARSENAL = Map.of(
            "meme", 4, "tomato", 7, "crocodile", 2, "voice", 4, "negative", 1,
            "apozh", 1, "replacement", 1, "object", 1, "poop", 1, "megaText", 1);

    public static final List<String> ARSENAL_KEYS = List.of(
            "meme", "tomato", "crocodile", "voice", "negative", "apozh", "replacement", "object", "poop", "megaText");

    /** Размер обоймы объявляет её владелец; здесь остаётся только ссылка. */
    public static final int MEME_LOADOUT_SIZE = SabotageArmoryPort.LOADOUT_SIZE;
    public static final long COOLDOWN_MS = 3000;
    public static final Set<String> PAUSABLE_PHASES = Set.of("turnIntro", "active", "appeal", "between");
    public static final Map<String, Integer> BUILTIN_MEMES = Map.of("builtin-bmw-drugoy-ne-znayu", 5000);

    private GameRules() {
    }

    /** Недостающие ключи добираются из базового набора — так же, как в asArsenal(). */
    public static Map<String, Integer> arsenal(Object value) {
        Map<String, Object> raw = Json.map(value);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : ARSENAL_KEYS) {
            Object stored = raw.get(key);
            long amount = stored == null ? BASE_ARSENAL.get(key) : Json.num(stored);
            result.put(key, (int) Math.max(0, amount));
        }
        return result;
    }

    public static Map<String, Object> arsenalAsMap(Map<String, Integer> arsenal) {
        return new LinkedHashMap<>(arsenal);
    }

    /** Награда за ход: помидоры и мемы за каждые 2–3 угаданных слова, крокодил за 5. */
    public static Map<String, Integer> rewardForScore(int score) {
        int n = Math.max(0, score);
        int meme = (n / 3) * 2;
        Map<String, Integer> reward = new LinkedHashMap<>();
        reward.put("tomato", (n / 2) * 2);
        reward.put("meme", meme);
        reward.put("voice", meme);
        reward.put("crocodile", n / 5);
        return reward;
    }

    /**
     * Редкие диверсии выдаются по номеру суммарно угаданного командой слова:
     * 4-е — негатив, 6-е — апож, 8-е — подмена, 9-е — объект, 11-е — какахи,
     * 13-е — мега текст.
     */
    public static List<String> specialRewardsBetween(int previousScore, int nextScore) {
        int from = Math.max(0, previousScore);
        int to = Math.max(from, nextScore);
        List<String> events = new ArrayList<>();
        for (int wordNumber = from + 1; wordNumber <= to; wordNumber++) {
            if (wordNumber % 4 == 0) events.add("negative");
            if (wordNumber % 6 == 0) events.add("apozh");
            if (wordNumber % 8 == 0) events.add("replacement");
            if (wordNumber % 9 == 0) events.add("object");
            if (wordNumber % 11 == 0) events.add("poop");
            if (wordNumber % 13 == 0) events.add("megaText");
        }
        return events;
    }

    /** Блокировки эффектов на объясняющем. */
    public static Map<String, Object> sabotageLocks(Room room) {
        Map<String, Object> raw = Json.map(room.getSabotageLocks());
        Map<String, Object> byAttacker = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : Json.map(raw.get("replacementRecordingByAttacker")).entrySet()) {
            String uid = entry.getKey().trim();
            long until = Math.max(0, Json.num(entry.getValue()));
            if (!uid.isEmpty() && until > 0) {
                byAttacker.put(uid, until);
            }
        }
        Map<String, Object> locks = new LinkedHashMap<>();
        locks.put("videoUntil", Math.max(0, Json.num(raw.get("videoUntil"))));
        locks.put("voiceUntil", Math.max(0, Json.num(raw.get("voiceUntil"))));
        locks.put("crocodileUntil", Math.max(0, Json.num(raw.get("crocodileUntil"))));
        locks.put("replacementUntil", Math.max(0, Json.num(raw.get("replacementUntil"))));
        locks.put("replacementTurnId", Json.str(raw.get("replacementTurnId")));
        locks.put("replacementRecordingUntil", Math.max(0, Json.num(raw.get("replacementRecordingUntil"))));
        locks.put("replacementRecordingByAttacker", byAttacker);
        locks.put("overlayUntil", Math.max(0, Json.num(raw.get("overlayUntil"))));
        return locks;
    }

    public static List<String> normalizedLoadout(List<String> memeLoadout) {
        List<String> loadout = new ArrayList<>();
        for (String id : memeLoadout == null ? List.<String>of() : memeLoadout) {
            if (id != null && !id.isBlank() && !loadout.contains(id)) {
                loadout.add(id);
            }
            if (loadout.size() >= MEME_LOADOUT_SIZE) {
                break;
            }
        }
        return loadout;
    }

    /** Циклическая очередь мемов игрока: доступные → резерв → переработка. */
    public static final class MemeQueue {
        public List<String> loadout = new ArrayList<>();
        public List<String> available = new ArrayList<>();
        public List<String> reserve = new ArrayList<>();
        public List<String> recycle = new ArrayList<>();
        public int cycleCursor;
    }

    public static MemeQueue memeQueue(RoomPlayer player) {
        return memeQueue(player.getMemeLoadout(), player.getMemeAvailableIds(), player.getMemeRecycleQueue(),
                player.getMemeReserveIds(), player.getUsedMemeIds(), player.getArsenal(),
                player.getMemeCycleCursor() == null ? 0 : player.getMemeCycleCursor());
    }

    /** Тот же расчёт для тест-бота: его состояние лежит в testBotRuntime комнаты. */
    public static MemeQueue memeQueue(Map<String, Object> runtime) {
        return memeQueue(Json.strings(runtime.get("memeLoadout")),
                runtime.containsKey("memeAvailableIds") ? Json.strings(runtime.get("memeAvailableIds")) : null,
                runtime.containsKey("memeRecycleQueue") ? Json.strings(runtime.get("memeRecycleQueue")) : null,
                runtime.containsKey("memeReserveIds") ? Json.strings(runtime.get("memeReserveIds")) : null,
                Json.strings(runtime.get("usedMemeIds")), runtime.get("arsenal"),
                (int) Json.num(runtime.get("memeCycleCursor")));
    }

    private static MemeQueue memeQueue(List<String> rawLoadout, List<String> availableIds, List<String> recycleIds,
                                       List<String> reserveIds, List<String> usedIds, Object arsenalValue,
                                       int cursor) {
        MemeQueue queue = new MemeQueue();
        queue.loadout = normalizedLoadout(rawLoadout);
        if (availableIds != null && recycleIds != null) {
            queue.available = filterToLoadout(availableIds, queue.loadout);
            queue.recycle = filterToLoadout(recycleIds, queue.loadout);
            if (reserveIds != null) {
                queue.reserve = filterToLoadout(reserveIds, queue.loadout);
            } else {
                for (String id : queue.loadout) {
                    if (!queue.available.contains(id) && !queue.recycle.contains(id)) {
                        queue.reserve.add(id);
                    }
                }
            }
            queue.cycleCursor = Math.max(0, cursor);
            return queue;
        }
        // Миграция партии, начатой до появления циклической очереди.
        Set<String> used = new HashSet<>(usedIds == null ? List.of() : usedIds);
        Map<String, Integer> arsenal = arsenal(arsenalValue);
        int unlockedCount = Math.min(queue.loadout.size(), used.size() + arsenal.get("meme"));
        for (int i = 0; i < queue.loadout.size(); i++) {
            String id = queue.loadout.get(i);
            if (used.contains(id)) {
                queue.recycle.add(id);
            } else if (i < unlockedCount) {
                queue.available.add(id);
            } else {
                queue.reserve.add(id);
            }
        }
        return queue;
    }

    private static List<String> filterToLoadout(List<String> ids, List<String> loadout) {
        List<String> result = new ArrayList<>();
        for (String id : ids) {
            if (loadout.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    /** Выдача N мемов: сначала из резерва, потом из переработки, потом по кругу. */
    public static MemeQueue grantMemes(MemeQueue queue, int amount) {
        int count = Math.max(0, amount);
        while (count > 0 && !queue.loadout.isEmpty()) {
            String next = null;
            if (!queue.reserve.isEmpty()) {
                next = queue.reserve.remove(0);
            } else if (!queue.recycle.isEmpty()) {
                next = queue.recycle.remove(0);
            } else {
                next = queue.loadout.get(queue.cycleCursor % queue.loadout.size());
                queue.cycleCursor = (queue.cycleCursor + 1) % queue.loadout.size();
            }
            if (next != null) {
                queue.available.add(next);
            }
            count--;
        }
        return queue;
    }

    public static void applyQueue(RoomPlayer player, MemeQueue queue) {
        player.setMemeAvailableIds(new ArrayList<>(queue.available));
        player.setMemeReserveIds(new ArrayList<>(queue.reserve));
        player.setMemeRecycleQueue(new ArrayList<>(queue.recycle));
        player.setMemeCycleCursor(queue.cycleCursor);
    }

    public static List<String> rosterForTeam(Room room, String teamId) {
        List<String> roster = new ArrayList<>();
        for (String uid : Json.strings(Json.map(room.getTeamRosters()).get(teamId))) {
            if (!roster.contains(uid)) {
                roster.add(uid);
            }
        }
        return roster;
    }

    /** Все игроки текущей партии — по составам команд, а не по списку в комнате. */
    public static List<String> allGamePlayers(Room room) {
        List<String> all = new ArrayList<>();
        for (Object value : Json.map(room.getTeamRosters()).values()) {
            for (String uid : Json.strings(value)) {
                if (!all.contains(uid)) {
                    all.add(uid);
                }
            }
        }
        return all;
    }

    /** Дедлайн хода: от turnStartedAt + длительность, с фолбэком на legacy turnEndsAt. */
    public static long currentTurnDeadline(Room room) {
        long startedAt = room.getTurnStartedAt() == null ? 0 : room.getTurnStartedAt().toEpochMilli();
        double durationSeconds = room.getTurnDurationSeconds() != null
                ? room.getTurnDurationSeconds()
                : (room.getTurnDuration() == null ? 60 : room.getTurnDuration());
        if (startedAt > 0 && durationSeconds > 0) {
            return startedAt + Math.round(durationSeconds * 1000);
        }
        long legacy = room.getTurnEndsAt() == null ? 0 : room.getTurnEndsAt();
        return legacy > 0 ? legacy : 0;
    }

    public static double turnDurationSeconds(Room room) {
        if (room.getTurnDurationSeconds() != null && room.getTurnDurationSeconds() > 0) {
            return room.getTurnDurationSeconds();
        }
        return room.getTurnDuration() == null ? 60 : room.getTurnDuration();
    }

    public static Set<String> testBotIds(Room room) {
        if (!Boolean.TRUE.equals(room.getIsTestRoom())) {
            return Set.of();
        }
        return new HashSet<>(room.getTestBotIds() == null ? List.of() : room.getTestBotIds());
    }

    /** История диверсий хранит последние 24 события текущей партии. */
    public static List<Object> appendRecentSabotage(Room room, Map<String, Object> event) {
        List<Object> recent = new ArrayList<>(Json.list(room.getSabotageEventsRecent()));
        recent.add(event);
        List<Object> filtered = new ArrayList<>();
        for (Object item : recent) {
            if (item instanceof Map<?, ?> map && Json.num(Json.map(map).get("gameNumber")) == room.getGameNumber()) {
                filtered.add(item);
            }
        }
        return filtered.size() <= 24 ? filtered : new ArrayList<>(filtered.subList(filtered.size() - 24, filtered.size()));
    }
}
