package ru.hothat.service.game.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatProperties;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.spi.MemeLibraryPort;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.model.user.AppUser;
import ru.hothat.repository.*;
import ru.hothat.service.game.GameRules;
import ru.hothat.service.game.GameService;
import ru.hothat.service.livekit.LiveKitService;
import ru.hothat.service.recording.RecordingService;
import ru.hothat.service.words.RankedWordService;
import ru.hothat.util.Divisions;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;
import ru.hothat.util.Shuffle;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private static final long ACTIVE_PLAYER_WINDOW_MS = 5 * 60 * 1000;
    private static final long HOST_INACTIVITY_MS = 3 * 60 * 1000;
    /** Сколько ждём вернувшегося игрока, прежде чем засчитать техническое поражение. */
    private static final long DISCONNECT_LIMIT_CASUAL_MS = 90000;
    private static final long DISCONNECT_LIMIT_RANKED_MS = 180000;
    private static final int REPLACEMENT_SLOTS = 3;
    private static final long REPLACEMENT_RECORD_MS = 10500;

    private static final Set<String> VOICE_TYPES = Set.of("voice_bogdan", "voice_prokurish", "voice_apozh");
    private static final Set<String> ALLOWED_SABOTAGE = Set.of("meme", "tomato", "crocodile",
            "voice_bogdan", "voice_prokurish", "voice_apozh", "negative", "replacement",
            "mask_kit_penot", "object", "poop", "mega_text", "fart");

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final GetterUser getterUser;
    private final SaverUser saverUser;
    /** Библиотека мемов переехала в v2: её отдаёт область media. */
    private final MemeLibraryPort memeLibrary;
    private final LiveKitService liveKitService;
    private final RecordingService recordingService;
    private final RankedWordService rankedWordService;
    private final HotHatProperties properties;

    // ───────────────────────────── общее ─────────────────────────────

    private Room requireRoom(String roomId) {
        return getterRoom.getById(roomId).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
    }

    private RoomPlayer requirePlayer(String roomId, String uid) {
        return getterRoom.getPlayer(roomId, uid).orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 403));
    }

    private void requireHost(Room room, HotHatUser user) {
        if (!user.uid().equals(room.getCreatedBy())) {
            throw ApiException.of("HOST_ONLY", 403);
        }
    }

    private boolean isOwner(HotHatUser user) {
        return properties.isOwnerEmail(user.email());
    }

    // ───────────────────────── подготовка партии ─────────────────────────

    /**
     * Игра стартует только с полностью укомплектованными командами по двое и
     * пятью заряженными мемами у каждого — это проверка перед раздачей слов.
     */
    @Override
    @Transactional
    public Map<String, Object> prepareGame(HotHatUser user, String roomId) {
        Room room = requireRoom(roomId);
        if (!List.of("setup", "finished").contains(room.getPhase())) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        requireHost(room, user);

        List<RoomTeam> teams = getterRoom.getTeams(roomId);
        List<String> order = teams.stream().map(RoomTeam::getTeamId).toList();
        if (order.size() < 2 || order.size() > 5 || new HashSet<>(order).size() != order.size()) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }
        List<RoomPlayer> players = getterRoom.getPlayers(roomId);
        if (players.stream().noneMatch(p -> p.getUid().equals(user.uid()))) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }
        // Источник правды тот же, что у экрана настройки, — player.teamId.
        List<RoomPlayer> assigned = players.stream()
                .filter(p -> p.getTeamId() != null && order.contains(p.getTeamId())).toList();
        if (assigned.size() != order.size() * 2) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        order.forEach(teamId -> counts.put(teamId, 0));
        for (RoomPlayer player : assigned) {
            counts.merge(player.getTeamId(), 1, Integer::sum);
            if (GameRules.normalizedLoadout(player.getMemeLoadout()).size() != GameRules.MEME_LOADOUT_SIZE) {
                throw ApiException.of("LOADOUT_REQUIRED:" + (player.getName() == null ? "Игрок" : player.getName()), 409);
            }
        }
        if (counts.values().stream().anyMatch(count -> count != 2)) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }

        room.setTeamOrder(new ArrayList<>(order));
        clearPauseState(room);
        room.setSabotageCooldownUntil(0L);
        room.setSabotageEvent(null);
        room.setSabotageLocks(new LinkedHashMap<>());
        room.setReplacementRecordings(new LinkedHashMap<>());
        room.setSpecialRewardProgressByTeam(new LinkedHashMap<>());
        room.setSpecialRewardCursorByTeam(new LinkedHashMap<>());
        saverRoom.save(room);

        Map<String, List<String>> byTeam = new LinkedHashMap<>();
        order.forEach(teamId -> byTeam.put(teamId, new ArrayList<>()));
        long now = System.currentTimeMillis();
        for (RoomPlayer player : assigned) {
            byTeam.get(player.getTeamId()).add(player.getUid());
            List<String> loadout = GameRules.normalizedLoadout(player.getMemeLoadout());
            int unlocked = GameRules.BASE_ARSENAL.get("meme");
            player.setArsenal(new LinkedHashMap<>(GameRules.BASE_ARSENAL));
            player.setUsedMemeIds(new ArrayList<>());
            player.setMemeAvailableIds(new ArrayList<>(loadout.subList(0, Math.min(unlocked, loadout.size()))));
            player.setMemeReserveIds(new ArrayList<>(loadout.subList(Math.min(unlocked, loadout.size()), loadout.size())));
            player.setMemeRecycleQueue(new ArrayList<>());
            player.setMemeCycleCursor(0);
            player.setSabotageCooldownUntil(0L);
            player.setLastSeenAt(now);
            saverRoom.savePlayer(player);
            saverRoom.deleteSpectator(roomId, player.getUid());
        }
        for (RoomTeam team : teams) {
            team.setMemberUids(byTeam.getOrDefault(team.getTeamId(), new ArrayList<>()));
            saverRoom.saveTeam(team);
        }
        return Map.of("players", assigned.size(), "spectators", Math.max(0, players.size() - assigned.size()));
    }

    private void clearPauseState(Room room) {
        room.setGamePaused(false);
        room.setHostPaused(false);
        room.setPauseReason(null);
        room.setPauseMissingUids(new ArrayList<>());
        room.setPauseMissingNames(new ArrayList<>());
        room.setPauseStartedAtMs(0L);
        room.setPausedTurnRemainingMs(0L);
        room.setPausedAppealRemainingMs(0L);
    }

    /**
     * Рейтинговая партия стартует сама, как только собрались все пары. Слова
     * генерируются из пула дивизиона с оглядкой на историю каждого игрока,
     * чтобы одно и то же слово не выпадало повторно.
     */
    @Override
    @Transactional
    public Map<String, Object> rankedAutostart(HotHatUser user, String roomId) {
        Room room = requireRoom(roomId);
        if (!Boolean.TRUE.equals(room.getRanked())) {
            throw ApiException.of("RANKED_ONLY", 409);
        }
        if (!"setup".equals(room.getPhase())) {
            return Map.of("started", !"setup".equals(room.getPhase()), "phase", room.getPhase());
        }
        int targetPlayers = Math.max(4, Math.min(10, room.effectiveMaxPlayers()));
        List<RoomPlayer> players = getterRoom.getPlayers(roomId);
        if (players.stream().noneMatch(p -> p.getUid().equals(user.uid()))) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }
        if (players.size() < targetPlayers) {
            return Map.of("started", false, "waiting", true, "count", players.size(), "targetPlayers", targetPlayers);
        }
        List<String> order = room.getTeamOrder();
        if (order.size() != targetPlayers / 2) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }
        Map<String, RoomTeam> teams = new LinkedHashMap<>();
        getterRoom.getTeams(roomId).forEach(team -> teams.put(team.getTeamId(), team));

        Map<String, Object> teamRosters = new LinkedHashMap<>();
        List<String> allUids = new ArrayList<>();
        for (String teamId : order) {
            RoomTeam team = teams.get(teamId);
            if (team == null) {
                throw ApiException.of("TEAMS_NOT_READY", 409);
            }
            List<String> uids = new ArrayList<>(new LinkedHashSet<>(team.getMemberUids()));
            if (uids.size() != 2) {
                throw ApiException.of("TEAMS_NOT_READY", 409);
            }
            teamRosters.put(teamId, uids);
            allUids.addAll(uids);
        }
        if (new HashSet<>(allUids).size() != targetPlayers) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }
        Set<String> presentUids = new HashSet<>();
        players.forEach(p -> presentUids.add(p.getUid()));
        if (!presentUids.containsAll(allUids)) {
            return Map.of("started", false, "waiting", true, "count", players.size(), "targetPlayers", targetPlayers);
        }

        String rankedLanguage = Divisions.normalize(room.getDivisionLanguage() != null
                ? room.getDivisionLanguage() : room.getGameLanguage());
        // Оболочки всех игроков — одним запросом. Раньше здесь стоял поштучный
        // getByUid в цикле: стол на десятерых стоил десяти обращений к базе
        // ровно в тот момент, когда партия стартует и ждать нельзя.
        Map<String, AppUser> shells = new HashMap<>();
        for (AppUser shell : getterUser.getByUids(allUids)) {
            shells.put(shell.getUid(), shell);
        }
        List<AppUser> profiles = new ArrayList<>();
        List<List<String>> histories = new ArrayList<>();
        for (String uid : allUids) {
            AppUser profile = shells.get(uid);
            profiles.add(profile);
            histories.add(profile == null ? List.of() : profile.getRankedWordHistory());
        }
        int wordCount = targetPlayers * 10;
        List<String> words = rankedWordService.generate(wordCount, histories, rankedLanguage);
        if (words.size() != wordCount) {
            throw ApiException.of("RANKED_WORDS_UNAVAILABLE", 500);
        }

        Map<String, Object> names = new LinkedHashMap<>();
        Map<String, RoomPlayer> playersByUid = new LinkedHashMap<>();
        players.forEach(p -> playersByUid.put(p.getUid(), p));
        for (int i = 0; i < allUids.size(); i++) {
            RoomPlayer player = playersByUid.get(allUids.get(i));
            if (player == null) {
                throw ApiException.of("TEAMS_NOT_READY", 409);
            }
            List<String> loadout = GameRules.normalizedLoadout(player.getMemeLoadout());
            if (loadout.size() != GameRules.MEME_LOADOUT_SIZE) {
                throw ApiException.of("LOADOUT_REQUIRED:" + (player.getName() == null ? "Игрок" : player.getName()), 409);
            }
            int unlocked = GameRules.BASE_ARSENAL.get("meme");
            player.setArsenal(new LinkedHashMap<>(GameRules.BASE_ARSENAL));
            player.setUsedMemeIds(new ArrayList<>());
            player.setMemeAvailableIds(new ArrayList<>(loadout.subList(0, Math.min(unlocked, loadout.size()))));
            player.setMemeReserveIds(new ArrayList<>(loadout.subList(Math.min(unlocked, loadout.size()), loadout.size())));
            player.setMemeRecycleQueue(new ArrayList<>());
            player.setMemeCycleCursor(0);
            player.setSabotageCooldownUntil(0L);
            saverRoom.savePlayer(player);
            names.put(allUids.get(i), Json.str(player.getName() == null ? "Игрок " + (i + 1) : player.getName(), 40));
        }
        for (String teamId : order) {
            RoomTeam team = teams.get(teamId);
            team.setScore(0);
            team.setMemberUids(Json.strings(teamRosters.get(teamId)));
            saverRoom.saveTeam(team);
        }

        int gameNumber = room.getGameNumber() + 1;
        room.setTeamRosters(teamRosters);
        room.setGamePlayerNamesByUid(names);
        room.setPhase("turnIntro");
        clearPauseState(room);
        room.setBag(new ArrayList<>(words));
        room.setCurrentWord(null);
        room.setWordCount(wordCount);
        room.setWordRevision(room.getWordRevision() + 1);
        room.setWordsLeft(wordCount);
        room.setCurrentTeamIndex(0);
        room.setCurrentTeamId(order.get(0));
        room.setExplainerUid(null);
        room.setExplainerName(null);
        room.setGuesserUid(null);
        room.setGuesserName(null);
        room.setCurrentTurnScore(0);
        room.setTurnStartedAt(null);
        room.setTurnDurationSeconds(null);
        room.setTurnEndsAt(0L);
        room.setTurnId(null);
        room.setTurnGuessedWords(new ArrayList<>());
        room.setLastGuessedWord(null);
        room.setAppealEndsAt(0L);
        room.setAppealVotes(new LinkedHashMap<>());
        room.setSabotageEvent(null);
        room.setSabotageCooldownUntil(0L);
        room.setSabotageLocks(new LinkedHashMap<>());
        room.setReplacementRecordings(new LinkedHashMap<>());
        room.setSpecialRewardProgressByTeam(new LinkedHashMap<>());
        room.setSpecialRewardCursorByTeam(new LinkedHashMap<>());
        room.setLastTurn(null);
        room.setGameNumber(gameNumber);
        saverRoom.save(room);

        // История выданных слов: последние 520 ключей на игрока.
        List<String> generatedKeys = words.stream().map(word -> rankedWordService.normalize(word, rankedLanguage)).toList();
        for (int i = 0; i < allUids.size(); i++) {
            AppUser profile = profiles.get(i);
            if (profile == null) {
                continue;
            }
            List<String> merged = new ArrayList<>();
            for (String word : profile.getRankedWordHistory()) {
                String key = rankedWordService.normalize(word, rankedLanguage);
                if (!key.isEmpty()) {
                    merged.add(key);
                }
            }
            merged.addAll(generatedKeys);
            List<String> unique = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int x = merged.size() - 1; x >= 0 && unique.size() < 520; x--) {
                String key = merged.get(x);
                if (key.isEmpty() || !seen.add(key)) {
                    continue;
                }
                unique.add(key);
            }
            Collections.reverse(unique);
            profile.setRankedWordHistory(unique);
            profile.setRankedWordHistoryUpdatedAt(Instant.now());
            saverUser.save(profile);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("started", true);
        result.put("wordCount", wordCount);
        result.put("gameNumber", gameNumber);
        return result;
    }

    // ───────────────────────── хозяин комнаты ─────────────────────────

    @Override
    @Transactional
    public Map<String, Object> setupHostActivity(HotHatUser user, String roomId, Map<String, Object> body) {
        Room room = requireRoom(roomId);
        if (!"setup".equals(room.getPhase())) {
            return Map.of("active", false);
        }
        requireHost(room, user);
        room.setHostLastSetupActivityAt(System.currentTimeMillis());
        room.setHostLastSetupActivityKind(Json.str(body.getOrDefault("kind", "activity"), 40));
        saverRoom.save(room);
        return Map.of("active", true, "remainingMs", HOST_INACTIVITY_MS);
    }

    @Override
    @Transactional
    public Map<String, Object> manualHostTransfer(HotHatUser user, String roomId, Map<String, Object> body) {
        String targetUid = Json.str(body.get("target_uid")).trim();
        if (targetUid.isEmpty() || targetUid.equals(user.uid())) {
            throw ApiException.of("HOST_TRANSFER_TARGET_INVALID", 400);
        }
        Room room = requireRoom(roomId);
        if (!"setup".equals(room.getPhase())) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        requireHost(room, user);
        RoomPlayer target = getterRoom.getPlayer(roomId, targetUid)
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
        if (!target.isActive(System.currentTimeMillis() - ACTIVE_PLAYER_WINDOW_MS)) {
            throw ApiException.of("PLAYER_NOT_ACTIVE", 409);
        }
        long now = System.currentTimeMillis();
        room.setCreatedBy(targetUid);
        room.setPreviousHostUid(null);
        room.setHostTransferredAt(now);
        room.setHostTransferType("manual");
        room.setHostTransferredBy(user.uid());
        room.setHostLastSetupActivityAt(now);
        saverRoom.save(room);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("transferred", true);
        result.put("newHostUid", targetUid);
        result.put("newHostName", Json.str(target.getName() == null ? "Игрок" : target.getName(), 40));
        return result;
    }

    /**
     * Если публичная комната укомплектована, а хозяин три минуты ничего не
     * делает, права переходят следующему активному участнику. Вернувшийся
     * прежний хозяин получает комнату обратно, пока передача не стала финальной.
     */
    @Override
    @Transactional
    public Map<String, Object> setupHostWatch(HotHatUser user, String roomId) {
        Room room = requireRoom(roomId);
        if (!"setup".equals(room.getPhase())) {
            return mapOfNullable("active", false, "remainingMs", null);
        }
        if (Boolean.TRUE.equals(room.getIsPrivate())) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("active", false);
            result.put("privateRoom", true);
            result.put("remainingMs", null);
            return result;
        }
        int target = Math.max(4, room.effectiveMaxPlayers());
        long cutoff = System.currentTimeMillis() - ACTIVE_PLAYER_WINDOW_MS;
        List<RoomPlayer> active = getterRoom.getPlayers(roomId).stream().filter(p -> p.isActive(cutoff)).toList();
        Set<String> activeUids = new HashSet<>();
        active.forEach(p -> activeUids.add(p.getUid()));
        String currentHostUid = Json.str(room.getCreatedBy());
        String previousHostUid = Json.str(room.getPreviousHostUid());
        long now = System.currentTimeMillis();

        if (!previousHostUid.isEmpty() && !previousHostUid.equals(currentHostUid) && activeUids.contains(previousHostUid)) {
            RoomPlayer restored = active.stream().filter(p -> p.getUid().equals(previousHostUid)).findFirst().orElse(null);
            room.setCreatedBy(previousHostUid);
            room.setPreviousHostUid(null);
            room.setHostTransferredAt(null);
            room.setHostLastSetupActivityAt(now);
            saverRoom.save(room);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("active", true);
            result.put("count", active.size());
            result.put("target", target);
            result.put("remainingMs", null);
            result.put("transferred", true);
            result.put("restored", true);
            result.put("newHostUid", previousHostUid);
            result.put("newHostName", restored == null || restored.getName() == null ? "хозяин комнаты" : restored.getName());
            return result;
        }

        if (active.size() != target) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("active", false);
            result.put("count", active.size());
            result.put("target", target);
            result.put("remainingMs", null);
            return result;
        }
        long lastActivityAt = room.getHostLastSetupActivityAt();
        if (lastActivityAt == 0) {
            room.setHostLastSetupActivityAt(now);
            saverRoom.save(room);
            return activeWatch(active.size(), target, HOST_INACTIVITY_MS);
        }
        long remainingMs = Math.max(0, lastActivityAt + HOST_INACTIVITY_MS - now);
        if (remainingMs > 0) {
            return activeWatch(active.size(), target, remainingMs);
        }
        // Кандидатом не может быть сам бездействующий хозяин.
        List<RoomPlayer> candidates = active.stream().filter(p -> !p.getUid().equals(currentHostUid)).toList();
        if (candidates.isEmpty()) {
            return activeWatch(active.size(), target, null);
        }
        RoomPlayer next = candidates.get(0);
        room.setCreatedBy(next.getUid());
        room.setHostLastSetupActivityAt(now);
        room.setHostTransferredAt(now);
        // Передача по бездействию окончательна: прежнего хозяина не возвращаем.
        room.setPreviousHostUid(null);
        saverRoom.save(room);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", true);
        result.put("count", active.size());
        result.put("target", target);
        result.put("remainingMs", null);
        result.put("transferred", true);
        result.put("newHostUid", next.getUid());
        result.put("newHostName", next.getName());
        return result;
    }

    private Map<String, Object> activeWatch(int count, int target, Long remainingMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", true);
        result.put("count", count);
        result.put("target", target);
        result.put("remainingMs", remainingMs);
        result.put("transferred", false);
        return result;
    }

    private Map<String, Object> mapOfNullable(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        return result;
    }

    /** Запись партии включает только владелец сервиса и только до старта. */
    @Override
    @Transactional
    public Map<String, Object> setRecordingPreference(HotHatUser user, String roomId, Map<String, Object> body) {
        if (!isOwner(user)) {
            throw ApiException.of("OWNER_ONLY", 403);
        }
        Room room = requireRoom(roomId);
        requirePlayer(roomId, user.uid());
        if (!"setup".equals(room.getPhase())) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        boolean recordGame = Json.bool(body.get("record_game"));
        room.setRecordGame(recordGame);
        room.setRecordingPreferenceUpdatedAt(Instant.now());
        room.setRecordingPreferenceUpdatedBy(user.uid());
        saverRoom.save(room);
        return Map.of("ok", true, "recordGame", recordGame);
    }

    // ───────────────────────── составы ─────────────────────────

    /** Каждый клик — полностью новая жеребьёвка, включая уже расставленных. */
    @Override
    @Transactional
    public Map<String, Object> randomizeTeams(HotHatUser user, String roomId) {
        Room room = requireRoom(roomId);
        if (!"setup".equals(room.getPhase())) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        requirePlayer(roomId, user.uid());

        List<RoomTeam> teams = getterRoom.getTeams(roomId);
        List<String> order = teams.stream().map(RoomTeam::getTeamId).toList();
        if (order.size() < 2 || order.size() > 5) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }
        long cutoff = System.currentTimeMillis() - ACTIVE_PLAYER_WINDOW_MS;
        List<RoomPlayer> activePlayers = getterRoom.getPlayers(roomId).stream().filter(p -> p.isActive(cutoff)).toList();
        if (activePlayers.isEmpty()) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }
        List<RoomPlayer> shuffled = Shuffle.of(activePlayers);
        int seats = order.size() * 2;
        List<RoomPlayer> selected = shuffled.subList(0, Math.min(seats, shuffled.size()));
        List<RoomPlayer> spectators = shuffled.subList(Math.min(seats, shuffled.size()), shuffled.size());

        List<String> slots = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            slots.addAll(Shuffle.of(order));
        }
        Map<String, List<String>> assignedByTeam = new LinkedHashMap<>();
        order.forEach(teamId -> assignedByTeam.put(teamId, new ArrayList<>()));

        long now = System.currentTimeMillis();
        for (int i = 0; i < selected.size(); i++) {
            RoomPlayer player = selected.get(i);
            String teamId = slots.get(i);
            assignedByTeam.get(teamId).add(player.getUid());
            player.setTeamId(teamId);
            player.setLastSeenAt(now);
            saverRoom.savePlayer(player);
            // Членство в команде важнее устаревшей зрительской записи с прошлой партии.
            saverRoom.deleteSpectator(roomId, player.getUid());
        }
        for (RoomPlayer player : spectators) {
            player.setTeamId(null);
            player.setLastSeenAt(now);
            saverRoom.savePlayer(player);
        }
        for (RoomTeam team : teams) {
            team.setMemberUids(assignedByTeam.getOrDefault(team.getTeamId(), new ArrayList<>()));
            saverRoom.saveTeam(team);
        }
        room.setTeamOrder(new ArrayList<>(order));
        saverRoom.save(room);

        return Map.of("assigned", selected.size(), "remaining", spectators.size(), "reshuffled", true);
    }

    @Override
    @Transactional
    public Map<String, Object> kickPlayer(HotHatUser user, String roomId, Map<String, Object> body) {
        Room room = requireRoom(roomId);
        if (!"setup".equals(room.getPhase())) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        requireHost(room, user);
        String targetUid = Json.str(body.get("target_uid")).trim();
        if (targetUid.isEmpty()) {
            targetUid = Json.str(body.get("targetUid")).trim();
        }
        if (targetUid.isEmpty()) {
            throw ApiException.of("KICK_TARGET_REQUIRED", 400);
        }
        if (targetUid.equals(user.uid())) {
            throw ApiException.of("KICK_SELF_FORBIDDEN", 409);
        }
        boolean hasPlayer = getterRoom.getPlayer(roomId, targetUid).isPresent();
        boolean hasSpectator = getterRoom.getSpectator(roomId, targetUid).isPresent();
        if (!hasPlayer && !hasSpectator) {
            return Map.of("removed", false, "alreadyGone", true, "targetUid", targetUid);
        }
        for (RoomTeam team : getterRoom.getTeams(roomId)) {
            if (team.getMemberUids().contains(targetUid)) {
                List<String> members = new ArrayList<>(team.getMemberUids());
                members.remove(targetUid);
                team.setMemberUids(members);
                saverRoom.saveTeam(team);
            }
        }
        saverRoom.deletePlayer(roomId, targetUid);
        saverRoom.deleteSpectator(roomId, targetUid);
        saverRoom.save(room);
        return Map.of("removed", true, "targetUid", targetUid);
    }

    // ───────────────────────── присутствие и пауза ─────────────────────────

    /**
     * Источник правды по присутствию — ростер LiveKit, а не heartbeat в базе:
     * вернувшийся во вкладку игрок должен снимать паузу сразу. Тест-боты в
     * LiveKit не заходят и на паузу не влияют.
     */
    @Override
    @Transactional
    public Map<String, Object> syncGamePresence(HotHatUser user, String roomId, Map<String, Object> body) {
        Room room = requireRoom(roomId);
        List<String> gamePlayers = GameRules.allGamePlayers(room);
        if (!gamePlayers.contains(user.uid())) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }
        Set<String> testBots = GameRules.testBotIds(room);
        List<String> expected = gamePlayers.stream().filter(uid -> !testBots.contains(uid)).toList();
        if (!GameRules.PAUSABLE_PHASES.contains(room.getPhase())) {
            return presenceResult(false, room.getPhase(), List.of());
        }

        String reason = Json.str(body.get("reason"));
        boolean callerDefinitelyLeft = List.of("livekit-reconnecting", "livekit-disconnected", "browser-pagehide")
                .contains(reason);
        if (callerDefinitelyLeft) {
            // Отключение фиксируем до опроса LiveKit: его ростер отдаёт ушедшего
            // ещё несколько секунд, и запоздалая проверка сняла бы паузу зря.
            getterRoom.getPlayer(roomId, user.uid()).ifPresent(player -> {
                player.setLastSeenAt(0L);
                player.setCameraEnabled(false);
                player.setMicrophoneEnabled(false);
                player.setMediaReadyAt(0L);
                saverRoom.savePlayer(player);
            });
        }

        Set<String> connected;
        try {
            connected = new HashSet<>(liveKitService.listParticipantIdentities(roomId));
            // Превью-подключения главной страницы не должны считаться игроками.
            List<String> previews = connected.stream().filter(id -> id.startsWith("preview-")).toList();
            for (String identity : previews) {
                connected.remove(identity);
                try {
                    liveKitService.removeParticipant(roomId, identity);
                } catch (RuntimeException e) {
                    log.debug("Не удалось отключить превью {}: {}", identity, e.getMessage());
                }
            }
            if (callerDefinitelyLeft) {
                connected.remove(user.uid());
            }
        } catch (RuntimeException e) {
            log.warn("Проверка присутствия недоступна: {}", e.getMessage());
            Map<String, Object> result = presenceResult(Boolean.TRUE.equals(room.getGamePaused()),
                    room.getPhase(), room.getPauseMissingUids());
            result.put("presenceUnavailable", true);
            return result;
        }

        Map<String, Object> names = Json.map(room.getGamePlayerNamesByUid());
        for (String uid : expected) {
            if (Json.str(names.get(uid)).isBlank()) {
                getterRoom.getPlayer(roomId, uid).ifPresent(player -> names.put(uid, Json.str(player.getName())));
            }
        }
        List<String> missing = expected.stream().filter(uid -> !connected.contains(uid)).toList();
        long now = System.currentTimeMillis();

        // Если из обычной игры исчезли разом все, партию некому доигрывать.
        if ("livekit-disconnected".equals(reason) && !expected.isEmpty()
                && missing.size() == expected.size() && !Boolean.TRUE.equals(room.getRanked())) {
            if (Boolean.TRUE.equals(room.getRecordGame())) {
                safeFinishRecording(roomId, room.getGameNumber(), "all_players_disconnected");
            }
            saverRoom.deleteRoomTree(roomId);
            Map<String, Object> result = presenceResult(false, "closed", missing);
            result.put("deleted", true);
            result.put("allDisconnected", true);
            return result;
        }

        if (!missing.isEmpty()) {
            long pauseStartedAtMs = Boolean.TRUE.equals(room.getGamePaused())
                    ? (room.getPauseStartedAtMs() == 0 ? now : room.getPauseStartedAtMs()) : now;
            long disconnectLimitMs = Boolean.TRUE.equals(room.getRanked())
                    ? DISCONNECT_LIMIT_RANKED_MS : DISCONNECT_LIMIT_CASUAL_MS;
            if (Boolean.TRUE.equals(room.getGamePaused()) && now - pauseStartedAtMs >= disconnectLimitMs) {
                // Массовый обрыв не наказывает никого: рейтинг такой партии аннулируется.
                boolean massFailure = missing.size() >= Math.max(3, (int) Math.ceil(expected.size() / 2.0));
                Map<String, Object> termination = new LinkedHashMap<>();
                termination.put("type", Boolean.TRUE.equals(room.getRanked()) ? "ranked_disconnect" : "casual_disconnect");
                termination.put("missingUids", missing);
                termination.put("missingNames", missing.stream().map(uid -> nameFor(names, uid)).toList());
                termination.put("noPenalty", Boolean.TRUE.equals(room.getRanked()) && massFailure);
                termination.put("endedAtMs", now);

                room.setPhase("finished");
                clearPauseState(room);
                room.setTechnicalTermination(termination);
                saverRoom.save(room);
                if (Boolean.TRUE.equals(room.getRecordGame())) {
                    safeFinishRecording(roomId, room.getGameNumber(),
                            Boolean.TRUE.equals(room.getRanked()) ? "ranked_disconnect" : "casual_disconnect");
                }
                Map<String, Object> result = presenceResult(false, "finished", missing);
                result.put("technicalFinished", true);
                result.put("ranked", Boolean.TRUE.equals(room.getRanked()));
                result.put("noPenalty", Boolean.TRUE.equals(room.getRanked()) && massFailure);
                return result;
            }
            boolean wasPaused = Boolean.TRUE.equals(room.getGamePaused());
            room.setGamePaused(true);
            room.setPauseReason("player_disconnected");
            room.setPauseMissingUids(new ArrayList<>(missing));
            room.setPauseMissingNames(missing.stream().map(uid -> nameFor(names, uid)).toList());
            room.setGamePlayerNamesByUid(names);
            room.setPauseStartedAtMs(pauseStartedAtMs);
            if (!wasPaused && "active".equals(room.getPhase())) {
                // Остаток хода замораживаем, чтобы вернуть его при снятии паузы.
                long deadline = GameRules.currentTurnDeadline(room);
                room.setPausedTurnRemainingMs(Math.max(0, deadline > 0 ? deadline - now
                        : Math.round(GameRules.turnDurationSeconds(room) * 1000)));
                room.setTurnStartedAt(null);
                room.setTurnEndsAt(0L);
            }
            if (!wasPaused && "appeal".equals(room.getPhase())) {
                room.setPausedAppealRemainingMs(Math.max(0, room.getAppealEndsAt() - now));
                room.setAppealEndsAt(0L);
            }
            saverRoom.save(room);
            Map<String, Object> result = presenceResult(true, room.getPhase(), missing);
            result.put("hostPaused", Boolean.TRUE.equals(room.getHostPaused()));
            return result;
        }

        // Пауза администратора переживает проверки присутствия: снять её может только хозяин.
        if (Boolean.TRUE.equals(room.getHostPaused())) {
            room.setGamePaused(true);
            room.setPauseReason("host_paused");
            room.setPauseMissingUids(new ArrayList<>());
            room.setPauseMissingNames(new ArrayList<>());
            saverRoom.save(room);
            Map<String, Object> result = presenceResult(true, room.getPhase(), List.of());
            result.put("hostPaused", true);
            return result;
        }
        if (!Boolean.TRUE.equals(room.getGamePaused())) {
            return presenceResult(false, room.getPhase(), List.of());
        }
        resumeFromPause(room, now);
        saverRoom.save(room);
        Map<String, Object> result = presenceResult(false, room.getPhase(), List.of());
        result.put("resumed", true);
        return result;
    }

    private void resumeFromPause(Room room, long now) {
        String phase = room.getPhase();
        long turnRemaining = Math.max(0, room.getPausedTurnRemainingMs());
        long appealRemaining = Math.max(0, room.getPausedAppealRemainingMs());
        clearPauseState(room);
        if ("active".equals(phase)) {
            room.setTurnStartedAt(Instant.ofEpochMilli(now));
            room.setTurnDurationSeconds(Math.max(0.1, turnRemaining / 1000.0));
            room.setTurnEndsAt(0L);
        }
        if ("appeal".equals(phase)) {
            room.setAppealEndsAt(now + appealRemaining);
        }
    }

    private void safeFinishRecording(String roomId, int gameNumber, String reason) {
        try {
            recordingService.finishRoomRecordingSystem(roomId, gameNumber, reason);
        } catch (RuntimeException e) {
            log.warn("Не удалось остановить запись комнаты {}: {}", roomId, e.getMessage());
        }
    }

    private static String nameFor(Map<String, Object> names, String uid) {
        String name = Json.str(names.get(uid)).trim();
        if (name.isEmpty()) {
            name = "Участник " + (uid.length() <= 4 ? uid : uid.substring(uid.length() - 4));
        }
        return Json.str(name, 40);
    }

    private Map<String, Object> presenceResult(boolean paused, String phase, List<String> missingUids) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paused", paused);
        result.put("phase", phase);
        result.put("missingUids", missingUids == null ? List.of() : missingUids);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> toggleManualPause(HotHatUser user, String roomId, Map<String, Object> body) {
        Room room = requireRoom(roomId);
        requireHost(room, user);
        if (!GameRules.PAUSABLE_PHASES.contains(room.getPhase())) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
        long now = System.currentTimeMillis();
        boolean shouldPause = Json.bool(body.get("paused"));

        if (shouldPause) {
            boolean wasPaused = Boolean.TRUE.equals(room.getGamePaused());
            room.setHostPaused(true);
            room.setGamePaused(true);
            room.setPauseReason("host_paused");
            room.setPauseStartedAtMs(wasPaused ? (room.getPauseStartedAtMs() == 0 ? now : room.getPauseStartedAtMs()) : now);
            if (!wasPaused && "active".equals(room.getPhase())) {
                long deadline = GameRules.currentTurnDeadline(room);
                room.setPausedTurnRemainingMs(Math.max(0, deadline > 0 ? deadline - now
                        : Math.round(GameRules.turnDurationSeconds(room) * 1000)));
                room.setTurnStartedAt(null);
                room.setTurnEndsAt(0L);
            }
            if (!wasPaused && "appeal".equals(room.getPhase())) {
                room.setPausedAppealRemainingMs(Math.max(0, room.getAppealEndsAt() - now));
                room.setAppealEndsAt(0L);
            }
            saverRoom.save(room);
            return Map.of("paused", true, "hostPaused", true);
        }

        // Пока кто-то не вернулся, снятие паузы хозяином не возобновляет игру.
        List<String> stillMissing = room.getPauseMissingUids() == null
                ? List.of() : new ArrayList<>(room.getPauseMissingUids());
        if (!stillMissing.isEmpty()) {
            room.setHostPaused(false);
            room.setGamePaused(true);
            room.setPauseReason("player_disconnected");
            saverRoom.save(room);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("paused", true);
            result.put("hostPaused", false);
            result.put("missingUids", stillMissing);
            return result;
        }
        resumeFromPause(room, now);
        saverRoom.save(room);
        return Map.of("paused", false, "hostPaused", false, "resumed", true);
    }

    // ───────────────────────── мемы в слотах ─────────────────────────

    /** Заменить заряженный мем можно в любой момент, но не своей активной команде. */
    @Override
    @Transactional
    public Map<String, Object> replaceMemeSlot(HotHatUser user, String roomId, Map<String, Object> body) {
        int slotIndex = (int) Json.num(body.get("slot_index"), -1);
        String nextMemeId = Json.str(body.get("meme_id")).trim();
        if (slotIndex < 0 || slotIndex >= GameRules.MEME_LOADOUT_SIZE) {
            throw ApiException.of("MEME_SLOT_INVALID", 400);
        }
        if (nextMemeId.isEmpty()) {
            throw ApiException.of("MEME_NOT_LOADED", 400);
        }
        if (!GameRules.BUILTIN_MEMES.containsKey(nextMemeId) && memeLibrary.find(nextMemeId).isEmpty()) {
            throw ApiException.of("MEME_NOT_LOADED", 404);
        }
        Room room = requireRoom(roomId);
        RoomPlayer player = requirePlayer(roomId, user.uid());
        if (!GameRules.allGamePlayers(room).contains(user.uid())) {
            throw ApiException.of("WEAPON_NOT_ELIGIBLE", 403);
        }
        if (!GameRules.PAUSABLE_PHASES.contains(room.getPhase())) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
        if (GameRules.rosterForTeam(room, room.getCurrentTeamId()).contains(user.uid())) {
            throw ApiException.of("ACTIVE_TEAM_LOADOUT_LOCKED", 409);
        }

        GameRules.MemeQueue queue = GameRules.memeQueue(player);
        if (queue.loadout.size() != GameRules.MEME_LOADOUT_SIZE) {
            throw ApiException.of("LOADOUT_REQUIRED", 409);
        }
        String oldMemeId = queue.loadout.get(slotIndex);
        if (oldMemeId.equals(nextMemeId)) {
            return Map.of("unchanged", true, "loadout", queue.loadout);
        }
        if (queue.loadout.contains(nextMemeId)) {
            throw ApiException.of("MEME_DUPLICATE", 409);
        }
        queue.loadout.set(slotIndex, nextMemeId);
        queue.available = replaceIn(queue.available, oldMemeId, nextMemeId);
        queue.reserve = replaceIn(queue.reserve, oldMemeId, nextMemeId);
        queue.recycle = replaceIn(queue.recycle, oldMemeId, nextMemeId);

        player.setMemeLoadout(new ArrayList<>(queue.loadout));
        GameRules.applyQueue(player, queue);
        player.setUsedMemeIds(replaceIn(player.getUsedMemeIds(), oldMemeId, nextMemeId));
        saverRoom.savePlayer(player);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loadout", queue.loadout);
        result.put("available", queue.available);
        result.put("reserve", queue.reserve);
        result.put("recycle", queue.recycle);
        result.put("slotIndex", slotIndex);
        result.put("oldMemeId", oldMemeId);
        result.put("memeId", nextMemeId);
        return result;
    }

    private static List<String> replaceIn(List<String> values, String from, String to) {
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            result.add(from.equals(value) ? to : value);
        }
        return result;
    }

    // ───────────────────────── диверсии ─────────────────────────

    /** Метаданные мема для события диверсии: длительность, заголовок, играбельный URL. */
    private Map<String, Object> memeEventMeta(String memeId) {
        Integer bundledDuration = GameRules.BUILTIN_MEMES.get(memeId);
        // Библиотека мемов переехала в v2 и принадлежит области media: этот
        // движок спрашивает её через порт. Читать meme_library он больше не
        // может — там остались только строки, выложенные до переезда, и мем,
        // загруженный сегодня, показал бы всей комнате чёрный экран.
        MemeLibraryPort.PlayableMeme meme = memeLibrary.find(memeId).orElse(null);
        if (meme == null && bundledDuration == null) {
            throw ApiException.of("MEME_NOT_FOUND", 404);
        }
        String publicOrigin = properties.publicSiteUrl();
        String fallbackSrc = "builtin-bmw-drugoy-ne-znayu".equals(memeId)
                ? publicOrigin + "/assets/memes/bmw-drugoy-ne-znayu.mp4" : "";
        String fallbackPoster = "builtin-bmw-drugoy-ne-znayu".equals(memeId)
                ? publicOrigin + "/assets/memes/bmw-drugoy-ne-znayu.webp" : "";

        long duration = meme != null && meme.durationMs() > 0 ? meme.durationMs()
                : (bundledDuration == null ? 5000 : bundledDuration);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durationMs", Math.max(400, Math.min(10000, duration)));
        result.put("title", Json.str(meme != null && meme.title() != null ? meme.title()
                : ("builtin-bmw-drugoy-ne-znayu".equals(memeId) ? "BMW — другой не знаю" : "Мем"), 120));
        // Встроенный ролик лежит в статике сайта, у остальных адрес собирает
        // область медиа по ключу объекта — своей копии ссылки у мема больше нет.
        result.put("src", firstNonBlank(meme == null ? null : meme.videoUrl(), fallbackSrc));
        result.put("poster", firstNonBlank(meme == null ? null : meme.posterUrl(), fallbackPoster));
        result.put("mediaPath", meme == null || meme.videoKey() == null ? "" : meme.videoKey());
        result.put("posterPath", meme == null || meme.posterKey() == null ? "" : meme.posterKey());
        result.put("storageProvider", meme == null || meme.videoKey() == null ? "" : "s3-compatible");
        return result;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /**
     * Диверсия по объясняющему. Проверок много: фаза, режим, принадлежность к
     * партии, кулдаун, взаимоисключающие эффекты и остаток боезапаса.
     */
    @Override
    @Transactional
    public Map<String, Object> useSabotage(HotHatUser user, String roomId, Map<String, Object> body) {
        String type = Json.str(body.get("type"));
        if (!ALLOWED_SABOTAGE.contains(type)) {
            throw ApiException.of("WEAPON_INVALID");
        }
        boolean isVoice = VOICE_TYPES.contains(type);
        boolean isPenot = "mask_kit_penot".equals(type);
        boolean isNegative = "negative".equals(type);
        boolean isReplacement = "replacement".equals(type);
        boolean isFart = "fart".equals(type);
        boolean isOverlay = List.of("object", "poop", "mega_text").contains(type);

        String memeId = "meme".equals(type) ? Json.str(body.get("meme_id")).trim() : "";
        String clipId = isReplacement ? Json.str(body.get("clip_id")).trim() : "";
        if (isReplacement && clipId.isEmpty()) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 400);
        }
        Map<String, Object> memeMeta = "meme".equals(type) ? memeEventMeta(memeId) : null;

        Room room = requireRoom(roomId);
        RoomPlayer player = requirePlayer(roomId, user.uid());
        long now = System.currentTimeMillis();

        if (!"active".equals(room.getPhase())) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
        if (Boolean.TRUE.equals(room.getGamePaused())) {
            throw ApiException.of("GAME_PAUSED", 409);
        }
        if (Json.str(room.getExplainerUid()).isEmpty()) {
            throw ApiException.of("EXPLAINER_MISSING", 409);
        }
        // Расширенный арсенал доступен только в режиме диверсий и не в тестовой комнате.
        boolean advanced = isVoice || isPenot || isNegative || isReplacement || isOverlay || isFart;
        if (advanced && (Boolean.TRUE.equals(room.getIsTestRoom()) || !"sabotage".equals(room.getGameMode()))) {
            throw ApiException.of("WEAPON_INVALID", 409);
        }
        if (isPenot && !isOwner(user)) {
            throw ApiException.of("OWNER_ONLY", 403);
        }
        if (!GameRules.allGamePlayers(room).contains(user.uid())) {
            throw ApiException.of("WEAPON_NOT_ELIGIBLE", 403);
        }
        List<String> activeRoster = GameRules.rosterForTeam(room, room.getCurrentTeamId());
        if (activeRoster.contains(user.uid())) {
            throw ApiException.of("ACTIVE_TEAM_CANNOT_ATTACK", 403);
        }
        if (!"tomato".equals(type) && !isFart && player.getSabotageCooldownUntil() > now) {
            throw ApiException.of("WEAPON_COOLDOWN", 429);
        }

        Map<String, Object> locks = GameRules.sabotageLocks(room);
        long turnDeadline = GameRules.currentTurnDeadline(room);
        long remainingMs = Math.max(0, turnDeadline - now);
        boolean replacementActive = Json.num(locks.get("replacementUntil")) > now;
        // Во время Подмены разрешены только помидоры и мемы — остальное сломало бы сцену.
        if (replacementActive && !"tomato".equals(type) && !"meme".equals(type) && !isFart) {
            throw ApiException.of("REPLACEMENT_ACTIVE", 409);
        }
        if ((isNegative || isPenot) && Json.num(locks.get("videoUntil")) > now) {
            throw ApiException.of("VIDEO_EFFECT_BUSY", 409);
        }
        if (isVoice && Json.num(locks.get("voiceUntil")) > now) {
            throw ApiException.of("VOICE_EFFECT_BUSY", 409);
        }
        if (isOverlay && Json.num(locks.get("overlayUntil")) > now) {
            throw ApiException.of("OVERLAY_EFFECT_BUSY", 409);
        }
        if (isReplacement) {
            if (!Json.str(locks.get("replacementTurnId")).isEmpty()
                    && Json.str(locks.get("replacementTurnId")).equals(Json.str(room.getTurnId()))) {
                throw ApiException.of("REPLACEMENT_ALREADY_USED", 409);
            }
            if (Json.num(locks.get("videoUntil")) > now || Json.num(locks.get("voiceUntil")) > now
                    || Json.num(locks.get("crocodileUntil")) > now) {
                throw ApiException.of("REPLACEMENT_CONFLICT", 409);
            }
            if (remainingMs < 11000) {
                throw ApiException.of("NOT_ENOUGH_TURN_TIME", 409);
            }
        }
        if (isNegative && remainingMs < 11000) {
            throw ApiException.of("NOT_ENOUGH_TURN_TIME", 409);
        }
        if ("voice_apozh".equals(type) && remainingMs < 16000) {
            throw ApiException.of("NOT_ENOUGH_TURN_TIME", 409);
        }

        Map<String, Integer> arsenal = GameRules.arsenal(player.getArsenal());
        String ammoKey = "voice_apozh".equals(type) ? "apozh"
                : isVoice ? "voice"
                : isNegative ? "negative"
                : isReplacement ? "replacement"
                : "mega_text".equals(type) ? "megaText"
                : type;
        // У владельца сервиса негатив и апож не расходуются в ноль.
        boolean ownerUnlimited = isOwner(user) && (isNegative || "voice_apozh".equals(type));
        if (!isPenot && !isFart && !ownerUnlimited && arsenal.getOrDefault(ammoKey, 0) <= 0) {
            throw ApiException.of("NO_AMMO", 409);
        }

        Map<String, Object> replacementRecordings = Json.map(room.getReplacementRecordings());
        Map<String, Object> replacementMeta = null;
        if (isReplacement) {
            replacementMeta = Json.map(replacementRecordings.get(clipId));
            if (replacementMeta.isEmpty() || !user.uid().equals(Json.str(replacementMeta.get("attackerUid")))) {
                throw ApiException.of("REPLACEMENT_CLIP_INVALID", 404);
            }
            if (!Json.bool(replacementMeta.get("ready"))) {
                throw ApiException.of("REPLACEMENT_NOT_READY", 409);
            }
            if (Json.num(replacementMeta.get("gameNumber")) != room.getGameNumber()) {
                throw ApiException.of("REPLACEMENT_CLIP_INVALID", 409);
            }
            if (!activeRoster.contains(Json.str(replacementMeta.get("targetUid")))) {
                throw ApiException.of("REPLACEMENT_WRONG_TARGET", 409);
            }
            // Свою же запись нельзя применить в том же ходу, где она снята.
            if (Json.str(replacementMeta.get("recordedTurnId")).equals(Json.str(room.getTurnId()))) {
                throw ApiException.of("REPLACEMENT_SAME_TURN", 409);
            }
            replacementRecordings.remove(clipId);
        }

        List<String> usedMemeIds = new ArrayList<>(new LinkedHashSet<>(
                player.getUsedMemeIds() == null ? List.<String>of() : player.getUsedMemeIds()));
        GameRules.MemeQueue queue = GameRules.memeQueue(player);
        if ("meme".equals(type)) {
            if (!queue.loadout.contains(memeId)) {
                throw ApiException.of("MEME_NOT_LOADED", 403);
            }
            int availableIndex = queue.available.indexOf(memeId);
            if (availableIndex < 0) {
                throw ApiException.of("MEME_IN_RESERVE", 409);
            }
            queue.available.remove(availableIndex);
            queue.recycle.add(memeId);
            usedMemeIds.add(memeId);
        }
        if (!isPenot && !isFart) {
            if (!ownerUnlimited || arsenal.getOrDefault(ammoKey, 0) > 0) {
                arsenal.put(ammoKey, arsenal.getOrDefault(ammoKey, 0) - 1);
            }
        }

        long durationMs = switch (type) {
            case "meme" -> Json.num(memeMeta.get("durationMs"));
            case "tomato" -> 2000;
            case "negative" -> 10000;
            case "voice_apozh" -> 15000;
            case "replacement", "object", "poop" -> 10000;
            case "mega_text" -> 15000;
            case "fart" -> 0;
            default -> isVoice || isPenot ? 30000
                    : Math.max(1000, (turnDeadline > 0 ? turnDeadline
                    : now + Math.round(GameRules.turnDurationSeconds(room) * 1000)) - now);
        };
        long effectiveDeadline = turnDeadline > 0 ? turnDeadline : now + durationMs;
        // Эффект не может пережить сам ход.
        long lockUntil = Math.min(effectiveDeadline, now + durationMs);
        if (isNegative || isPenot) locks.put("videoUntil", lockUntil);
        if (isVoice) locks.put("voiceUntil", lockUntil);
        if ("crocodile".equals(type)) locks.put("crocodileUntil", lockUntil);
        if (isOverlay) locks.put("overlayUntil", lockUntil);
        if (isReplacement) {
            locks.put("replacementUntil", lockUntil);
            locks.put("replacementTurnId", Json.str(room.getTurnId()));
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "sab_" + Ids.hex(10));
        event.put("type", type);
        event.put("memeId", memeId.isEmpty() ? null : memeId);
        event.put("clipId", clipId.isEmpty() ? null : clipId);
        event.put("recordedTurnId", replacementMeta == null ? null : Json.str(replacementMeta.get("recordedTurnId")));
        event.put("attackerUid", user.uid());
        event.put("attackerName", Json.str(player.getName() == null ? "Игрок" : player.getName(), 40));
        event.put("targetUid", isReplacement ? Json.str(replacementMeta.get("targetUid")) : Json.str(room.getExplainerUid()));
        event.put("createdAtMs", now);
        event.put("durationMs", durationMs);
        event.put("gameNumber", room.getGameNumber());
        if (isOverlay) {
            event.put("x", Math.max(0.02, Math.min(0.98, Json.dbl(body.get("x"), 0.5))));
            event.put("y", Math.max(0.02, Math.min(0.98, Json.dbl(body.get("y"), 0.5))));
        }
        if ("meme".equals(type)) {
            event.put("memeTitle", Json.str(memeMeta.get("title"), 120));
            event.put("memeSrc", Json.str(memeMeta.get("src"), 2200));
            event.put("memePoster", Json.str(memeMeta.get("poster"), 2200));
            event.put("memeMediaPath", Json.str(memeMeta.get("mediaPath"), 500));
            event.put("memePosterPath", Json.str(memeMeta.get("posterPath"), 500));
            event.put("memeStorageProvider", Json.str(memeMeta.get("storageProvider"), 40));
        }

        long nextCooldownUntil = ("tomato".equals(type) || isFart)
                ? Math.max(0, player.getSabotageCooldownUntil()) : now + GameRules.COOLDOWN_MS;
        player.setArsenal(GameRules.arsenalAsMap(arsenal));
        player.setUsedMemeIds(usedMemeIds);
        GameRules.applyQueue(player, queue);
        player.setSabotageCooldownUntil(nextCooldownUntil);
        saverRoom.savePlayer(player);

        room.setSabotageEvent(event);
        room.setSabotageEventsRecent(GameRules.appendRecentSabotage(room, event));
        room.setSabotageCooldownUntil(0L);
        room.setSabotageLocks(locks);
        if (isReplacement) {
            room.setReplacementRecordings(replacementRecordings);
        }
        saverRoom.save(room);

        broadcast(roomId, event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", event);
        result.put("arsenal", arsenal);
        result.put("cooldownUntil", nextCooldownUntil);
        return result;
    }

    private void broadcast(String roomId, Map<String, Object> event) {
        try {
            liveKitService.sendSabotage(roomId, event);
        } catch (RuntimeException e) {
            // Рассылка — ускорение; состояние в базе остаётся источником правды.
            log.warn("Рассылка диверсии в {} не прошла: {}", roomId, e.getMessage());
        }
    }

    // ───────────────────────── записи Подмены ─────────────────────────

    /**
     * Атакующий записывает 10-секундный клип объясняющего, чтобы подменить им
     * картинку в следующем ходе. Слотов три, запись у каждого атакующего своя.
     */
    @Override
    @Transactional
    public Map<String, Object> createReplacementRecording(HotHatUser user, String roomId) {
        Room room = requireRoom(roomId);
        requirePlayer(roomId, user.uid());
        long now = System.currentTimeMillis();

        if (!"active".equals(room.getPhase())) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
        if (Boolean.TRUE.equals(room.getGamePaused())) {
            throw ApiException.of("GAME_PAUSED", 409);
        }
        if (Boolean.TRUE.equals(room.getIsTestRoom()) || !"sabotage".equals(room.getGameMode())) {
            throw ApiException.of("WEAPON_INVALID", 409);
        }
        if (Json.str(room.getExplainerUid()).isEmpty()) {
            throw ApiException.of("EXPLAINER_MISSING", 409);
        }
        if (!GameRules.allGamePlayers(room).contains(user.uid())) {
            throw ApiException.of("WEAPON_NOT_ELIGIBLE", 403);
        }
        if (GameRules.rosterForTeam(room, room.getCurrentTeamId()).contains(user.uid())) {
            throw ApiException.of("ACTIVE_TEAM_CANNOT_ATTACK", 403);
        }
        if (Math.max(0, GameRules.currentTurnDeadline(room) - now) < 11000) {
            throw ApiException.of("NOT_ENOUGH_TURN_TIME", 409);
        }

        Map<String, Object> recordings = Json.map(room.getReplacementRecordings());
        // Незавершённая запись (закрыли вкладку) не должна вечно занимать слот.
        recordings.entrySet().removeIf(entry -> {
            Map<String, Object> item = Json.map(entry.getValue());
            return !Json.bool(item.get("ready")) && now - Json.num(item.get("createdAtMs")) > 20000;
        });
        long owned = recordings.values().stream().map(Json::map)
                .filter(item -> user.uid().equals(Json.str(item.get("attackerUid")))
                        && Json.num(item.get("gameNumber")) == room.getGameNumber())
                .count();
        // Слоты не связаны с боезапасом: заряд тратится только при применении клипа.
        if (owned >= REPLACEMENT_SLOTS) {
            throw ApiException.of("REPLACEMENT_RECORD_LIMIT", 409);
        }
        Map<String, Object> locks = GameRules.sabotageLocks(room);
        Map<String, Object> byAttacker = Json.map(locks.get("replacementRecordingByAttacker"));
        if (Json.num(byAttacker.get(user.uid())) > now) {
            throw ApiException.of("REPLACEMENT_RECORD_BUSY", 409);
        }

        String recordId = "repl_" + Ids.hex(10);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", recordId);
        item.put("attackerUid", user.uid());
        item.put("targetUid", Json.str(room.getExplainerUid()));
        item.put("recordedTurnId", Json.str(room.getTurnId()));
        item.put("gameNumber", room.getGameNumber());
        item.put("createdAtMs", now);
        recordings.put(recordId, item);

        // Блокировка ставится на конкретного атакующего: десять игроков могут
        // снимать разных объясняющих одновременно.
        locks.put("replacementRecordingUntil", 0L);
        byAttacker.put(user.uid(), now + REPLACEMENT_RECORD_MS);
        locks.put("replacementRecordingByAttacker", byAttacker);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "sab_" + Ids.hex(10));
        event.put("type", "replacement_record");
        event.put("attackerUid", user.uid());
        event.put("targetUid", Json.str(room.getExplainerUid()));
        event.put("clipId", recordId);
        event.put("recordedTurnId", Json.str(room.getTurnId()));
        event.put("gameNumber", room.getGameNumber());
        event.put("createdAtMs", now);
        event.put("durationMs", 10000);

        room.setReplacementRecordings(recordings);
        room.setSabotageLocks(locks);
        room.setSabotageEvent(event);
        room.setSabotageEventsRecent(GameRules.appendRecentSabotage(room, event));
        saverRoom.save(room);

        broadcast(roomId, event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", event);
        result.put("arsenal", GameRules.arsenal(requirePlayer(roomId, user.uid()).getArsenal()));
        result.put("recordId", recordId);
        return result;
    }

    /** Итог записи присылает тот, кого снимали: готово — сохраняем, нет — стираем. */
    @Override
    @Transactional
    public Map<String, Object> replacementRecordingResult(HotHatUser user, String roomId, Map<String, Object> body) {
        String clipId = Json.str(body.get("clip_id")).trim();
        boolean ready = Boolean.TRUE.equals(body.get("ready"));
        if (clipId.isEmpty()) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 400);
        }
        Room room = requireRoom(roomId);
        Map<String, Object> recordings = Json.map(room.getReplacementRecordings());
        Map<String, Object> item = Json.map(recordings.get(clipId));
        if (item.isEmpty() || !user.uid().equals(Json.str(item.get("targetUid")))) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 404);
        }
        Map<String, Object> locks = GameRules.sabotageLocks(room);
        Map<String, Object> byAttacker = Json.map(locks.get("replacementRecordingByAttacker"));
        byAttacker.remove(Json.str(item.get("attackerUid")));
        locks.put("replacementRecordingUntil", 0L);
        locks.put("replacementRecordingByAttacker", byAttacker);

        if (ready) {
            item.put("ready", true);
            item.put("readyAtMs", System.currentTimeMillis());
            recordings.put(clipId, item);
        } else {
            recordings.remove(clipId);
        }
        room.setReplacementRecordings(recordings);
        room.setSabotageLocks(locks);
        saverRoom.save(room);
        return Map.of("clipId", clipId, "ready", ready);
    }

    @Override
    @Transactional
    public Map<String, Object> discardReplacementRecording(HotHatUser user, String roomId, Map<String, Object> body) {
        String clipId = Json.str(body.get("clip_id")).trim();
        if (clipId.isEmpty()) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 400);
        }
        Room room = requireRoom(roomId);
        Map<String, Object> recordings = Json.map(room.getReplacementRecordings());
        Map<String, Object> item = Json.map(recordings.get(clipId));
        if (item.isEmpty() || !user.uid().equals(Json.str(item.get("attackerUid")))) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 404);
        }
        recordings.remove(clipId);
        Map<String, Object> locks = GameRules.sabotageLocks(room);
        Map<String, Object> byAttacker = Json.map(locks.get("replacementRecordingByAttacker"));
        byAttacker.remove(user.uid());
        locks.put("replacementRecordingUntil", 0L);
        locks.put("replacementRecordingByAttacker", byAttacker);
        room.setReplacementRecordings(recordings);
        room.setSabotageLocks(locks);
        saverRoom.save(room);
        return Map.of("discarded", true, "clipId", clipId);
    }

    // ───────────────────────── апелляция ─────────────────────────

    /** Голос — переключатель: повторный клик по слову снимает свой голос. */
    @Override
    @Transactional
    public Map<String, Object> voteAppeal(HotHatUser user, String roomId, Map<String, Object> body) {
        String wordId = Json.str(body.get("word_id")).trim();
        if (wordId.isEmpty()) {
            throw ApiException.of("WORD_ID_REQUIRED");
        }
        Room room = requireRoom(roomId);
        if (!"appeal".equals(room.getPhase())) {
            throw ApiException.of("APPEAL_NOT_ACTIVE", 409);
        }
        if (Boolean.TRUE.equals(room.getGamePaused())) {
            throw ApiException.of("GAME_PAUSED", 409);
        }
        if (System.currentTimeMillis() > room.getAppealEndsAt()) {
            throw ApiException.of("APPEAL_CLOSED", 409);
        }
        Map<String, Object> lastTurn = Json.map(room.getLastTurn());
        String teamId = Json.str(lastTurn.get("teamId"));
        if (teamId.isEmpty()) {
            teamId = Json.str(room.getCurrentTeamId());
        }
        List<String> activeRoster = GameRules.rosterForTeam(room, teamId);
        List<String> eligible = GameRules.allGamePlayers(room).stream()
                .filter(uid -> !activeRoster.contains(uid)).toList();
        if (!eligible.contains(user.uid())) {
            throw ApiException.of("APPEAL_NOT_ELIGIBLE", 403);
        }
        List<Map<String, Object>> guessedWords = Json.maps(lastTurn.get("guessedWords"));
        if (guessedWords.stream().noneMatch(item -> wordId.equals(Json.str(item.get("id"))))) {
            throw ApiException.of("WORD_NOT_IN_TURN", 404);
        }
        Map<String, Object> votes = Json.map(room.getAppealVotes());
        List<String> mine = new ArrayList<>(Json.strings(votes.get(user.uid())));
        if (mine.contains(wordId)) {
            mine.remove(wordId);
        } else {
            mine.add(wordId);
        }
        votes.put(user.uid(), mine);
        room.setAppealVotes(votes);
        saverRoom.save(room);
        return Map.of("votes", mine, "eligibleCount", eligible.size());
    }

    /**
     * Подведение итогов хода: слова, за отмену которых проголосовало большинство
     * неиграющих, возвращаются в шляпу и вычитаются из счёта, после чего команда
     * получает награды и очередь переходит следующей команде.
     */
    @Override
    @Transactional
    public Map<String, Object> finalizeAppeal(HotHatUser user, String roomId) {
        Room room = requireRoom(roomId);
        if (!"appeal".equals(room.getPhase())) {
            return Map.of("alreadyFinalized", true);
        }
        long now = System.currentTimeMillis();
        long pausedAppealRemainingMs = Math.max(0, room.getPausedAppealRemainingMs());
        // Пауза, у которой истёк остаток времени апелляции, не мешает подвести итог.
        boolean finalizeExpiredPause = Boolean.TRUE.equals(room.getGamePaused()) && pausedAppealRemainingMs <= 0;
        if (Boolean.TRUE.equals(room.getGamePaused()) && !finalizeExpiredPause) {
            throw ApiException.of("GAME_PAUSED", 409);
        }
        if (!Boolean.TRUE.equals(room.getGamePaused()) && now < room.getAppealEndsAt()) {
            throw ApiException.of("APPEAL_STILL_OPEN", 409);
        }
        if (!GameRules.allGamePlayers(room).contains(user.uid())) {
            throw ApiException.of("APPEAL_NOT_ELIGIBLE", 403);
        }

        Map<String, Object> lastTurn = Json.map(room.getLastTurn());
        String teamId = Json.str(lastTurn.get("teamId"));
        if (teamId.isEmpty()) {
            teamId = Json.str(room.getCurrentTeamId());
        }
        List<String> activeRoster = GameRules.rosterForTeam(room, teamId);
        List<String> eligible = GameRules.allGamePlayers(room).stream()
                .filter(uid -> !activeRoster.contains(uid)).toList();
        int majority = eligible.size() / 2 + 1;
        Map<String, Object> votes = Json.map(room.getAppealVotes());
        List<Map<String, Object>> guessedWords = Json.maps(lastTurn.get("guessedWords"));

        List<Map<String, Object>> invalid = new ArrayList<>();
        for (Map<String, Object> item : guessedWords) {
            String id = Json.str(item.get("id"));
            int count = 0;
            for (String uid : eligible) {
                if (Json.strings(votes.get(uid)).contains(id)) {
                    count++;
                }
            }
            if (count >= majority) {
                invalid.add(item);
            }
        }
        List<String> invalidIds = invalid.stream().map(item -> Json.str(item.get("id"))).toList();
        int preliminaryScore = (int) Math.max(0, lastTurn.containsKey("score")
                ? Json.num(lastTurn.get("score")) : guessedWords.size());
        int finalScore = Math.max(0, preliminaryScore - invalid.size());
        Map<String, Integer> rewards = GameRules.rewardForScore(finalScore);

        Set<String> testBots = GameRules.testBotIds(room);
        List<String> realRoster = activeRoster.stream().filter(uid -> !testBots.contains(uid)).toList();

        Map<String, Object> specialProgress = Json.map(room.getSpecialRewardProgressByTeam());
        Map<String, Object> specialCursorByTeam = Json.map(room.getSpecialRewardCursorByTeam());
        int previousSpecialScore = (int) Math.max(0, Json.num(specialProgress.get(teamId)));
        int nextSpecialScore = previousSpecialScore + finalScore;
        List<String> specialEvents = Boolean.TRUE.equals(room.getIsTestRoom())
                ? List.of() : GameRules.specialRewardsBetween(previousSpecialScore, nextSpecialScore);

        // Редкие диверсии раздаются по кругу внутри команды, чтобы не копились у одного.
        int specialCursor = (int) Math.max(0, Json.num(specialCursorByTeam.get(teamId)));
        Map<String, Map<String, Integer>> specialRewardsByUid = new LinkedHashMap<>();
        for (String special : specialEvents) {
            if (realRoster.isEmpty()) {
                break;
            }
            String recipientUid = realRoster.get(specialCursor % realRoster.size());
            specialCursor++;
            specialRewardsByUid
                    .computeIfAbsent(recipientUid, uid -> new LinkedHashMap<>(Map.of(
                            "negative", 0, "apozh", 0, "replacement", 0, "object", 0, "poop", 0, "megaText", 0)))
                    .merge(special, 1, Integer::sum);
        }
        specialProgress.put(teamId, nextSpecialScore);
        specialCursorByTeam.put(teamId, specialCursor);

        // Каждое угаданное слово уже прибавило очко команде — апелляция только вычитает.
        RoomTeam team = getterRoom.getTeam(roomId, teamId).orElse(null);
        if (team != null) {
            if (!invalid.isEmpty()) {
                team.setScore(team.getScore() - invalid.size());
            }
            saverRoom.saveTeam(team);
        }

        for (String uid : realRoster) {
            RoomPlayer player = getterRoom.getPlayer(roomId, uid).orElse(null);
            if (player == null) {
                continue;
            }
            Map<String, Integer> arsenal = GameRules.arsenal(player.getArsenal());
            arsenal.merge("tomato", rewards.get("tomato"), Integer::sum);
            arsenal.merge("meme", rewards.get("meme"), Integer::sum);
            arsenal.merge("voice", rewards.get("voice"), Integer::sum);
            arsenal.merge("crocodile", rewards.get("crocodile"), Integer::sum);
            Map<String, Integer> special = specialRewardsByUid.getOrDefault(uid, Map.of());
            for (String key : List.of("negative", "apozh", "replacement", "object", "poop", "megaText")) {
                arsenal.merge(key, Math.max(0, special.getOrDefault(key, 0)), Integer::sum);
            }
            GameRules.MemeQueue queue = GameRules.memeQueue(player);
            if (rewards.get("meme") > 0) {
                GameRules.grantMemes(queue, rewards.get("meme"));
            }
            player.setArsenal(GameRules.arsenalAsMap(arsenal));
            GameRules.applyQueue(player, queue);
            saverRoom.savePlayer(player);
        }

        // У тест-ботов нет строки игрока — их арсенал живёт в testBotRuntime комнаты.
        Map<String, Object> testBotRuntime = Json.map(room.getTestBotRuntime());
        for (String uid : activeRoster) {
            if (!testBots.contains(uid)) {
                continue;
            }
            Map<String, Object> current = Json.map(testBotRuntime.get(uid));
            Map<String, Integer> arsenal = GameRules.arsenal(current.get("arsenal"));
            arsenal.merge("tomato", rewards.get("tomato"), Integer::sum);
            arsenal.merge("meme", rewards.get("meme"), Integer::sum);
            arsenal.merge("voice", rewards.get("voice"), Integer::sum);
            arsenal.merge("crocodile", rewards.get("crocodile"), Integer::sum);
            GameRules.MemeQueue queue = GameRules.memeQueue(current);
            if (rewards.get("meme") > 0) {
                GameRules.grantMemes(queue, rewards.get("meme"));
            }
            current.put("arsenal", GameRules.arsenalAsMap(arsenal));
            current.put("memeAvailableIds", queue.available);
            current.put("memeReserveIds", queue.reserve);
            current.put("memeRecycleQueue", queue.recycle);
            current.put("memeCycleCursor", queue.cycleCursor);
            testBotRuntime.put(uid, current);
        }

        List<String> returnedWords = invalid.stream()
                .map(item -> Json.str(item.get("word")).trim())
                .filter(word -> !word.isEmpty()).toList();
        List<String> bag = new ArrayList<>(room.getBag());
        bag.addAll(returnedWords);
        bag = Shuffle.of(bag);

        List<String> order = room.getTeamOrder();
        int nextIndex = order.isEmpty() ? 0 : (room.getCurrentTeamIndex() + 1) % order.size();
        String nextTeamId = order.isEmpty() ? null : order.get(nextIndex);
        boolean finished = bag.isEmpty();

        room.setPhase(finished ? "finished" : "turnIntro");
        boolean keepPaused = finalizeExpiredPause && !finished;
        room.setGamePaused(keepPaused);
        room.setPauseReason(keepPaused ? (room.getPauseReason() == null ? "player_disconnected" : room.getPauseReason()) : null);
        if (!keepPaused) {
            room.setPauseMissingUids(new ArrayList<>());
            room.setPauseMissingNames(new ArrayList<>());
            room.setPauseStartedAtMs(0L);
        } else if (room.getPauseStartedAtMs() == 0) {
            room.setPauseStartedAtMs(now);
        }
        room.setPausedTurnRemainingMs(0L);
        room.setPausedAppealRemainingMs(0L);
        room.setBag(bag);
        room.setWordsLeft(bag.size());
        room.setCurrentTeamIndex(nextIndex);
        room.setCurrentTeamId(finished ? teamId : nextTeamId);
        room.setCurrentWord(null);
        room.setExplainerUid(null);
        room.setExplainerName(null);
        room.setGuesserUid(null);
        room.setGuesserName(null);
        room.setCurrentTurnScore(0);
        room.setTurnStartedAt(null);
        room.setTurnDurationSeconds(null);
        room.setTurnEndsAt(0L);
        room.setTurnId(null);
        room.setTurnGuessedWords(new ArrayList<>());
        room.setLastGuessedWord(null);
        room.setAppealEndsAt(0L);
        room.setAppealVotes(new LinkedHashMap<>());
        room.setSabotageEvent(null);
        room.setSabotageCooldownUntil(0L);
        room.setTestBotRuntime(testBotRuntime);

        Map<String, Object> nextLastTurn = new LinkedHashMap<>(lastTurn);
        nextLastTurn.put("finalScore", finalScore);
        nextLastTurn.put("invalidWordIds", invalidIds);
        nextLastTurn.put("rewards", rewards);
        nextLastTurn.put("specialRewardsByUid", specialRewardsByUid);
        nextLastTurn.put("finalizedAtMs", now);
        room.setLastTurn(nextLastTurn);
        room.setSpecialRewardProgressByTeam(specialProgress);
        room.setSpecialRewardCursorByTeam(specialCursorByTeam);
        saverRoom.save(room);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("finalScore", finalScore);
        result.put("invalidWordIds", invalidIds);
        result.put("rewards", rewards);
        result.put("specialRewardsByUid", specialRewardsByUid);
        result.put("finished", finished);
        return result;
    }
}
