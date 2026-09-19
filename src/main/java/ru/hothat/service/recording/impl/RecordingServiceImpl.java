package ru.hothat.service.recording.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatProperties;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.media.GameRecording;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterMedia;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverMedia;
import ru.hothat.service.livekit.LiveKitService;
import ru.hothat.service.recording.RecordingService;
import ru.hothat.service.storage.ObjectStorageService;
import ru.hothat.util.Divisions;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingServiceImpl implements RecordingService {

    /** Коды состояний LiveKit Egress: 0 STARTING … 6 LIMIT_REACHED. */
    private static final Map<String, Integer> EGRESS_STATUS = Map.ofEntries(
            Map.entry("EGRESS_STARTING", 0), Map.entry("STARTING", 0),
            Map.entry("EGRESS_ACTIVE", 1), Map.entry("ACTIVE", 1),
            Map.entry("EGRESS_ENDING", 2), Map.entry("ENDING", 2),
            Map.entry("EGRESS_COMPLETE", 3), Map.entry("COMPLETE", 3),
            Map.entry("EGRESS_FAILED", 4), Map.entry("FAILED", 4),
            Map.entry("EGRESS_ABORTED", 5), Map.entry("ABORTED", 5),
            Map.entry("EGRESS_LIMIT_REACHED", 6), Map.entry("LIMIT_REACHED", 6));

    private final GetterRoom getterRoom;
    private final GetterMedia getterMedia;
    private final SaverMedia saverMedia;
    private final LiveKitService liveKitService;
    private final ObjectStorageService storage;
    private final HotHatProperties properties;

    @Value("${livekit.api-secret:}")
    private String livekitSecret;

    @Value("${livekit.api-key:}")
    private String livekitKey;

    @Value("${livekit.url:}")
    private String livekitUrl;

    @Value("${s3.access-key:}")
    private String s3AccessKey;

    @Value("${s3.secret-key:}")
    private String s3SecretKey;

    @Value("${s3.region:us-east-1}")
    private String s3Region;

    @Value("${s3.force-path-style:true}")
    private boolean s3ForcePathStyle;

    // ───────────────────────────── подписи ─────────────────────────────

    private String hmac(String payload) {
        if (livekitSecret == null || livekitSecret.isBlank()) {
            throw ApiException.of("LIVEKIT_NOT_CONFIGURED", 503);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(livekitSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось подписать ссылку записи", e);
        }
    }

    @Override
    public String viewSignature(String roomId, int gameNumber) {
        return hmac("hot-hat-recording-view|" + Json.str(roomId).trim() + "|" + Math.max(0, gameNumber));
    }

    @Override
    public boolean verifyViewSignature(String roomId, int gameNumber, String signature) {
        return constantTimeEquals(viewSignature(roomId, gameNumber), signature);
    }

    @Override
    public String egressWebhookSignature(String roomId, int gameNumber) {
        return hmac("hot-hat-egress-webhook|" + Json.str(roomId).trim() + "|" + Math.max(0, gameNumber));
    }

    @Override
    public boolean verifyEgressWebhookSignature(String roomId, int gameNumber, String signature) {
        return constantTimeEquals(egressWebhookSignature(roomId, gameNumber), signature);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return a.length == b.length && MessageDigest.isEqual(a, b);
    }

    @Override
    public String recordingId(String roomId, int gameNumber) {
        return Json.str(roomId).trim() + "-" + Math.max(0, gameNumber);
    }

    @Override
    public String objectPath(String roomId, int gameNumber) {
        return "game-recordings/" + Json.str(roomId).trim() + "/game-" + Math.max(0, gameNumber) + ".mp4";
    }

    /**
     * Egress открывает обычный клиент HOT-HAT в режиме рекордера: он логинится
     * по подписи и показывает ту же игру, что видят участники.
     */
    @Override
    public String recorderPageUrl(String roomId, int gameNumber) {
        return properties.publicSiteUrl() + "/?recorder=1"
                + "&roomId=" + encode(roomId)
                + "&gameNumber=" + Math.max(0, gameNumber)
                + "&sig=" + encode(viewSignature(roomId, gameNumber));
    }

    private String webEgressUrl(String roomId, int gameNumber) {
        String recorderToken = liveKitService.accessToken(new LiveKitService.AccessTokenRequest(
                "hot-hat-recorder-" + Ids.hex(8), "HOT-HAT Recorder", roomId, null,
                false, true, true, true, true, false, false, true, 7200));
        return recorderPageUrl(roomId, gameNumber)
                + "&url=" + encode(livekitUrl)
                + "&token=" + encode(recorderToken)
                + "&layout=hot-hat";
    }

    private String egressWebhookUrl(String roomId, int gameNumber) {
        return properties.apiOrigin() + "/api/recording-egress"
                + "?roomId=" + encode(roomId)
                + "&gameNumber=" + Math.max(0, gameNumber)
                + "&sig=" + encode(egressWebhookSignature(roomId, gameNumber));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    // ───────────────────────── запуск и остановка ─────────────────────────

    @Override
    @Transactional
    public Map<String, Object> startRoomRecording(HotHatUser user, String roomId, Integer requestedGameNumber) {
        String id = Json.str(roomId).trim();
        Room room = getterRoom.getById(id).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        if (!Boolean.TRUE.equals(room.getRecordGame())) {
            return Map.of("ok", true, "skipped", true, "reason", "recording-disabled");
        }
        String phase = room.getPhase() == null ? "setup" : room.getPhase();
        if ("closed".equals(phase)) {
            return Map.of("ok", true, "skipped", true, "reason", "room-closed");
        }
        if (getterRoom.getPlayer(id, user.uid()).isEmpty()) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }
        int currentGameNumber = Math.max(0, room.getGameNumber());
        // В setup запись прогревается на следующую партию, в игре — на текущую.
        int defaultGameNumber = "setup".equals(phase) ? currentGameNumber + 1 : currentGameNumber;
        int gameNumber = Math.max(0, requestedGameNumber == null ? defaultGameNumber : requestedGameNumber);
        boolean validGameNumber = "setup".equals(phase)
                ? gameNumber == currentGameNumber + 1
                : gameNumber == currentGameNumber;
        if (!validGameNumber) {
            throw ApiException.of("RECORDING_GAME_NUMBER_MISMATCH", 409);
        }
        if (!storage.isConfigured()) {
            throw ApiException.of("S3_RECORDING_STORAGE_NOT_CONFIGURED", 503);
        }

        boolean prewarmed = "setup".equals(phase);
        String recordingId = recordingId(id, gameNumber);
        long now = System.currentTimeMillis();
        GameRecording recording = getterMedia.getRecording(recordingId).orElse(null);
        if (recording != null) {
            boolean alreadyRunning = (recording.getEgressId() != null && !recording.getEgressId().isBlank())
                    || List.of("starting", "active", "complete", "processing").contains(recording.getStatus());
            // Секундная блокировка не даёт двум клиентам стартовать один Egress дважды.
            if (alreadyRunning || recording.getStartLockAtMs() > now - 30000) {
                return Map.of("ok", true, "started", false, "recording", publicRow(recording));
            }
        } else {
            recording = GameRecording.builder().id(recordingId).build();
        }
        recording.setRoomId(id);
        recording.setGameNumber(gameNumber);
        recording.setStatus("starting");
        recording.setStartLockAtMs(now);
        if (recording.getStartedAtMs() == 0) {
            recording.setStartedAtMs(now);
        }
        // Сигналы прошлой попытки сбрасываем, чтобы рекордер не выиграл гонку.
        recording.setRecorderReadyAtMs(0L);
        recording.setRecorderStartSignalAtMs(0L);
        recording.setRecorderLivekitIdentity("");
        recording.setEgressActiveAtMs(0L);
        recording.setLivekitStatus(0);
        recording.setPrewarmed(Boolean.TRUE.equals(recording.getPrewarmed()) || prewarmed);
        if (recording.getObjectPath() == null) {
            recording.setObjectPath(objectPath(id, gameNumber));
        }
        recording.setStorageProvider("s3-compatible");
        recording.setStorageBucket(storage.bucket());
        saverMedia.saveRecording(recording);

        try {
            applyRoomMeta(recording, room, gameNumber);
            Map<String, Object> fileOutput = new LinkedHashMap<>();
            fileOutput.put("filepath", objectPath(id, gameNumber));
            fileOutput.put("disable_manifest", true);
            fileOutput.put("s3", s3UploadConfig());
            Map<String, Object> webhook = new LinkedHashMap<>();
            webhook.put("url", egressWebhookUrl(id, gameNumber));
            webhook.put("signing_key", livekitKey);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("url", webEgressUrl(id, gameNumber));
            request.put("audio_only", false);
            request.put("video_only", false);
            request.put("file_outputs", List.of(fileOutput));
            request.put("webhooks", List.of(webhook));
            Map<String, Object> info = liveKitService.egress("StartWebEgress", request);

            int initialStatus = normalizeStatus(info.get("status"));
            String egressId = Json.str(info.getOrDefault("egress_id", info.getOrDefault("egressId", "")));
            recording.setEgressId(egressId.isEmpty() ? recording.getEgressId() : egressId);
            recording.setStatus(initialStatus == 1 ? "active" : "starting");
            recording.setLivekitStatus(initialStatus >= 0 ? initialStatus : recording.getLivekitStatus());
            if (initialStatus == 1 && recording.getEgressActiveAtMs() == 0) {
                recording.setEgressActiveAtMs(now);
            }
            recording.setStartedAtMs(now);
            recording.setStartError(null);
            recording.setRecordingView("hot-hat-live-game-web-egress-v1");
            recording.setSecretWordRecorded(true);
            recording.setPrewarmed(prewarmed);
            recording.setTerminationReason(null);
            saverMedia.saveRecording(recording);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("started", true);
            result.put("recordingId", recordingId);
            result.put("egressId", recording.getEgressId());
            return result;
        } catch (RuntimeException e) {
            recording.setStatus("failed");
            recording.setStartError(Json.str(e.getMessage(), 600));
            recording.setExpiresAtMs(now + RECORDING_TTL_MS);
            saverMedia.saveRecording(recording);
            throw e;
        }
    }

    private Map<String, Object> s3UploadConfig() {
        Map<String, Object> s3 = new LinkedHashMap<>();
        s3.put("access_key", s3AccessKey);
        s3.put("secret", s3SecretKey);
        s3.put("region", s3Region);
        s3.put("endpoint", storage.endpoint());
        s3.put("bucket", storage.bucket());
        s3.put("force_path_style", s3ForcePathStyle);
        return s3;
    }

    /** Состав, команды и результат партии — они попадают в карточку записи. */
    private void applyRoomMeta(GameRecording recording, Room room, int gameNumber) {
        List<RoomPlayer> players = getterRoom.getPlayers(room.getId());
        List<RoomTeam> teams = getterRoom.getTeams(room.getId());

        List<Map<String, Object>> participants = new ArrayList<>();
        List<String> participantUids = new ArrayList<>();
        for (RoomPlayer player : players) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", player.getUid());
            row.put("nickname", Json.str(player.getName() == null ? "Игрок" : player.getName(), 80));
            row.put("teamId", player.getTeamId());
            row.put("isTestBot", Boolean.TRUE.equals(player.getIsTestBot()));
            participants.add(row);
            if (!Boolean.TRUE.equals(player.getIsTestBot())) {
                participantUids.add(player.getUid());
            }
        }
        List<Map<String, Object>> teamRows = new ArrayList<>();
        for (RoomTeam team : teams) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", team.getTeamId());
            row.put("name", Json.str(team.getName() == null ? "Команда" : team.getName(), 100));
            row.put("memberUids", team.getMemberUids());
            row.put("rankedTeamId", team.getRankedTeamId());
            row.put("score", team.getScore());
            teamRows.add(row);
        }
        int winningScore = teams.stream().mapToInt(RoomTeam::getScore).max().orElse(0);
        List<String> winnerTeamIds = new ArrayList<>();
        List<String> winnerTeamNames = new ArrayList<>();
        int totalScore = 0;
        for (RoomTeam team : teams) {
            totalScore += team.getScore();
            if (team.getScore() == winningScore) {
                winnerTeamIds.add(team.getTeamId());
                winnerTeamNames.add(team.getName());
            }
        }
        if (teams.isEmpty()) {
            winningScore = 0;
        }

        recording.setRoomId(room.getId());
        recording.setGameNumber(gameNumber);
        recording.setRoomName(Json.str(room.getName() == null ? room.getId() : room.getName(), 120));
        recording.setGameMode(room.getGameMode());
        recording.setRanked(Boolean.TRUE.equals(room.getRanked()));
        recording.setIsPrivate(Boolean.TRUE.equals(room.getIsPrivate()));
        recording.setIsTestRoom(Boolean.TRUE.equals(room.getIsTestRoom()));
        recording.setDivisionLanguage(Divisions.normalize(room.getDivisionLanguage()));
        recording.setGameLanguage(Divisions.normalize(room.getGameLanguage()));
        recording.setParticipants(participants);
        recording.setParticipantUids(participantUids);
        recording.setTeams(teamRows);
        recording.setWinnerTeamIds(winnerTeamIds);
        recording.setWinnerTeamNames(winnerTeamNames);
        recording.setWinningScore(winningScore);
        recording.setTotalScore(totalScore);
        recording.setWordCount(room.getWordCount());
    }

    private static int normalizeStatus(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        String key = Json.str(value).trim().toUpperCase();
        if (key.isEmpty()) {
            return -1;
        }
        Integer mapped = EGRESS_STATUS.get(key);
        if (mapped != null) {
            return mapped;
        }
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    @Transactional
    public Map<String, Object> finishRoomRecording(HotHatUser user, String roomId, Integer gameNumber) {
        return finishCommon(roomId, gameNumber, user.uid(), null);
    }

    @Override
    @Transactional
    public Map<String, Object> finishRoomRecordingSystem(String roomId, Integer gameNumber, String terminationReason) {
        return finishCommon(roomId, gameNumber, null, terminationReason);
    }

    private Map<String, Object> finishCommon(String roomId, Integer requestedGameNumber, String userUid,
                                             String terminationReason) {
        String id = Json.str(roomId).trim();
        Room room = getterRoom.getById(id).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        if (!Boolean.TRUE.equals(room.getRecordGame())) {
            return Map.of("ok", true, "skipped", true, "reason", "recording-disabled");
        }
        int gameNumber = Math.max(0, requestedGameNumber == null ? room.getGameNumber() : requestedGameNumber);
        GameRecording recording = getterMedia.getRecording(recordingId(id, gameNumber)).orElse(null);
        if (recording == null) {
            return Map.of("ok", true, "skipped", true, "reason", "not-started");
        }
        if (userUid != null) {
            boolean allowed = recording.getParticipantUids().contains(userUid)
                    || getterRoom.getPlayer(id, userUid).isPresent();
            if (!allowed) {
                throw ApiException.of("PLAYER_NOT_FOUND", 403);
            }
        }
        String storedStatus = Json.str(recording.getStatus());
        if (recording.getFinishedAtMs() > 0 && List.of("complete", "failed", "deleted").contains(storedStatus)) {
            if (terminationReason != null && recording.getTerminationReason() == null) {
                recording.setTerminationReason(terminationReason);
                saverMedia.saveRecording(recording);
            }
            return Map.of("ok", true, "finished", false, "recording", publicRow(recording));
        }

        // Повторный StopEgress безопасен: LiveKit просто подтвердит уже завершённую
        // запись, а зависшую ACTIVE наконец закроет и выгрузит файл.
        Map<String, Object> info = null;
        if (recording.getEgressId() != null && !recording.getEgressId().isBlank()
                && List.of("starting", "active", "processing").contains(storedStatus)) {
            try {
                info = liveKitService.egress("StopEgress", Map.of("egress_id", recording.getEgressId()));
            } catch (RuntimeException e) {
                String message = Json.str(e.getMessage()).toLowerCase();
                boolean benign = message.contains("not found") || message.contains("not active")
                        || message.contains("already") || message.contains("complete");
                if (!benign) {
                    throw e;
                }
            }
        }
        if (info == null && recording.getEgressId() != null && !recording.getEgressId().isBlank()) {
            info = fetchEgressInfo(recording.getEgressId());
        }

        if (recording.getFinishedAtMs() == 0) {
            applyRoomMeta(recording, room, gameNumber);
        }
        long now = System.currentTimeMillis();
        int statusCode = normalizeStatus(info == null ? recording.getLivekitStatus() : info.get("status"));
        String finalStatus = statusCode == 3 ? "complete" : (statusCode >= 4 ? "failed" : "processing");
        Map<String, Object> file = fileResult(info);
        recording.setStatus(finalStatus);
        recording.setLivekitStatus(statusCode >= 0 ? statusCode : null);
        if (recording.getFinishedAtMs() == 0) {
            recording.setFinishedAtMs(now);
        }
        recording.setExpiresAtMs(recording.getSavedCount() > 0 ? null : now + RECORDING_TTL_MS);
        recording.setDurationNs(pickLong(file, "duration", recording.getDurationNs()));
        recording.setSizeBytes(pickLong(file, "size", recording.getSizeBytes()));
        recording.setEgressError(Json.str(info == null ? recording.getEgressError() : info.get("error"), 600));
        recording.setTerminationReason(terminationReason != null ? terminationReason : recording.getTerminationReason());
        recording.setLastStopAttemptAtMs(now);
        saverMedia.saveRecording(recording);

        return Map.of("ok", true, "finished", true, "recordingId", recording.getId());
    }

    private Map<String, Object> fetchEgressInfo(String egressId) {
        try {
            Map<String, Object> listed = liveKitService.egress("ListEgress", Map.of("egress_id", egressId));
            List<Object> items = Json.list(listed.get("items"));
            for (Object item : items) {
                Map<String, Object> info = Json.map(item);
                String id = Json.str(info.getOrDefault("egress_id", info.getOrDefault("egressId", "")));
                if (egressId.equals(id)) {
                    return info;
                }
            }
            return items.isEmpty() ? null : Json.map(items.get(0));
        } catch (RuntimeException e) {
            log.warn("Статус Egress {} пока недоступен: {}", egressId, e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> fileResult(Map<String, Object> info) {
        if (info == null) {
            return Map.of();
        }
        List<Object> results = Json.list(info.getOrDefault("file_results", info.get("fileResults")));
        return results.isEmpty() ? Map.of() : Json.map(results.get(0));
    }

    private static long pickLong(Map<String, Object> file, String key, long fallback) {
        long value = Json.num(file.get(key));
        return value != 0 ? value : fallback;
    }

    // ───────────────────────── вебхук и синхронизация ─────────────────────────

    @Override
    @Transactional
    public Map<String, Object> applyEgressWebhook(String roomId, int gameNumber, Map<String, Object> event) {
        Object rawInfo = event.get("egressInfo");
        if (rawInfo == null) {
            rawInfo = event.get("egress_info");
        }
        if (rawInfo == null) {
            rawInfo = event.get("egress");
        }
        if (!(rawInfo instanceof Map<?, ?>)) {
            return Map.of("ok", true, "ignored", true, "reason", "no-egress-info");
        }
        Map<String, Object> info = Json.map(rawInfo);
        GameRecording recording = getterMedia.getRecording(recordingId(roomId, gameNumber)).orElse(null);
        if (recording == null) {
            return Map.of("ok", true, "ignored", true, "reason", "recording-not-found");
        }
        String incomingEgressId = Json.str(info.getOrDefault("egress_id", info.getOrDefault("egressId", "")));
        if (recording.getEgressId() != null && !recording.getEgressId().isBlank()
                && !incomingEgressId.isEmpty() && !recording.getEgressId().equals(incomingEgressId)) {
            return Map.of("ok", true, "ignored", true, "reason", "egress-id-mismatch");
        }

        int statusCode = normalizeStatus(info.get("status"));
        Map<String, Object> file = fileResult(info);
        long now = System.currentTimeMillis();
        String status = Json.str(recording.getStatus().isEmpty() ? "processing" : recording.getStatus());
        if (statusCode == 0) status = "starting";
        else if (statusCode == 1) status = "active";
        else if (statusCode == 2) status = "processing";
        else if (statusCode == 3) status = "complete";
        else if (statusCode >= 4) status = "failed";

        recording.setStatus(status);
        recording.setLivekitStatus(statusCode >= 0 ? statusCode : recording.getLivekitStatus());
        if (!incomingEgressId.isEmpty()) {
            recording.setEgressId(incomingEgressId);
        }
        // ACTIVE — первое состояние, доказывающее, что пайплайн записи реально пошёл.
        if (statusCode == 1 && recording.getEgressActiveAtMs() == 0) {
            recording.setEgressActiveAtMs(now);
        }
        recording.setDurationNs(pickLong(file, "duration", recording.getDurationNs()));
        recording.setSizeBytes(pickLong(file, "size", recording.getSizeBytes()));
        recording.setEgressError(Json.str(info.getOrDefault("error", info.getOrDefault("details",
                recording.getEgressError() == null ? "" : recording.getEgressError())), 600));
        long endedAtNs = Json.num(info.getOrDefault("ended_at", info.get("endedAt")));
        if (endedAtNs > 0) {
            recording.setEgressEndedAtMs(Math.round(endedAtNs / 1_000_000.0));
        }
        if (statusCode >= 3) {
            if (recording.getFinishedAtMs() == 0) {
                recording.setFinishedAtMs(now);
            }
            recording.setExpiresAtMs(recording.getSavedCount() > 0 ? null
                    : (recording.getExpiresAtMs() != null && recording.getExpiresAtMs() > 0
                    ? recording.getExpiresAtMs() : now + RECORDING_TTL_MS));
        }
        saverMedia.saveRecording(recording);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("recordingId", recording.getId());
        result.put("status", status);
        result.put("livekitStatus", statusCode);
        return result;
    }

    /**
     * Одно обращение к карточке может починить зависшую запись, но опроса нет:
     * повторные запросы к LiveKit не чаще раза в 15 секунд, StopEgress — в 30.
     */
    @Override
    @Transactional
    public GameRecording refresh(GameRecording recording) {
        if (recording.getObjectPath() == null || recording.getObjectPath().isBlank()) {
            return recording;
        }
        long now = System.currentTimeMillis();
        String status = Json.str(recording.getStatus());

        if (recording.getEgressId() != null && !recording.getEgressId().isBlank()
                && List.of("starting", "active", "processing").contains(status)
                && now - recording.getLastEgressSyncAtMs() > 15000) {
            Map<String, Object> info = fetchEgressInfo(recording.getEgressId());
            if (info != null) {
                applyEgressSnapshot(recording, info, now);
                recording.setLastEgressSyncAtMs(now);
            }
        }

        if ("processing".equals(Json.str(recording.getStatus()))
                && recording.getEgressId() != null && !recording.getEgressId().isBlank()
                && now - recording.getFinishedAtMs() > 15000
                && now - recording.getLastStopAttemptAtMs() > 30000) {
            Map<String, Object> stopInfo = null;
            try {
                stopInfo = liveKitService.egress("StopEgress", Map.of("egress_id", recording.getEgressId()));
            } catch (RuntimeException e) {
                log.warn("Повторная остановка записи {} отложена: {}", recording.getId(), e.getMessage());
            }
            if (stopInfo == null) {
                stopInfo = fetchEgressInfo(recording.getEgressId());
            }
            if (stopInfo != null) {
                applyEgressSnapshot(recording, stopInfo, now);
            }
            recording.setLastStopAttemptAtMs(now);
            recording.setLastEgressSyncAtMs(now);
        }

        // Статус 3 у LiveKit означает COMPLETE — не держим карточку в processing.
        if (recording.getLivekitStatus() != null && recording.getLivekitStatus() == 3) {
            recording.setStatus("complete");
            if (recording.getFinishedAtMs() == 0) {
                recording.setFinishedAtMs(now);
            }
        }
        storage.contentLength(recording.getObjectPath()).ifPresent(size -> {
            recording.setStatus("complete");
            recording.setSizeBytes(size);
        });
        return saverMedia.saveRecording(recording);
    }

    private void applyEgressSnapshot(GameRecording recording, Map<String, Object> info, long now) {
        int statusCode = normalizeStatus(info.getOrDefault("status", recording.getLivekitStatus()));
        Map<String, Object> file = fileResult(info);
        recording.setLivekitStatus(statusCode >= 0 ? statusCode : recording.getLivekitStatus());
        recording.setDurationNs(pickLong(file, "duration", recording.getDurationNs()));
        recording.setSizeBytes(pickLong(file, "size", recording.getSizeBytes()));
        recording.setEgressError(Json.str(info.getOrDefault("error", info.getOrDefault("details",
                recording.getEgressError() == null ? "" : recording.getEgressError())), 600));
        if (statusCode == 3) {
            recording.setStatus("complete");
        } else if (statusCode >= 4) {
            recording.setStatus("failed");
        }
        if (statusCode >= 3) {
            if (recording.getFinishedAtMs() == 0) {
                recording.setFinishedAtMs(now);
            }
            recording.setExpiresAtMs(recording.getSavedCount() > 0 ? null
                    : (recording.getExpiresAtMs() != null && recording.getExpiresAtMs() > 0
                    ? recording.getExpiresAtMs() : now + RECORDING_TTL_MS));
        }
    }

    // ───────────────────────── сигналы рекордера ─────────────────────────

    @Override
    @Transactional
    public void markRecorderReady(String roomId, int gameNumber, String phase, String identity) {
        GameRecording recording = getterMedia.getRecording(recordingId(roomId, gameNumber))
                .orElseGet(() -> GameRecording.builder()
                        .id(recordingId(roomId, gameNumber)).roomId(roomId).gameNumber(gameNumber).build());
        recording.setRecorderReadyAtMs(System.currentTimeMillis());
        recording.setRecorderReadyPhase(phase);
        if (identity != null && !identity.isBlank()) {
            recording.setRecorderLivekitIdentity(identity);
        }
        saverMedia.saveRecording(recording);
    }

    @Override
    @Transactional
    public void markRecorderStarted(String roomId, int gameNumber, String phase, String identity) {
        GameRecording recording = getterMedia.getRecording(recordingId(roomId, gameNumber))
                .orElseGet(() -> GameRecording.builder()
                        .id(recordingId(roomId, gameNumber)).roomId(roomId).gameNumber(gameNumber).build());
        recording.setRecorderStartSignalAtMs(System.currentTimeMillis());
        recording.setRecorderStartPhase(phase);
        if (identity != null && !identity.isBlank()) {
            recording.setRecorderLivekitIdentity(identity);
        }
        saverMedia.saveRecording(recording);
    }

    // ───────────────────────── выдача и уборка ─────────────────────────

    @Override
    public Map<String, Object> publicRow(GameRecording d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("roomId", d.getRoomId() == null ? "" : d.getRoomId());
        row.put("gameNumber", d.getGameNumber());
        row.put("roomName", d.getRoomName() != null ? d.getRoomName()
                : (d.getRoomId() != null ? d.getRoomId() : "Игра"));
        row.put("gameMode", d.getGameMode());
        row.put("ranked", d.getRanked());
        row.put("isPrivate", d.getIsPrivate());
        row.put("isTestRoom", d.getIsTestRoom());
        row.put("divisionLanguage", Divisions.normalize(d.getDivisionLanguage()));
        row.put("gameLanguage", Divisions.normalize(d.getGameLanguage() == null
                ? d.getDivisionLanguage() : d.getGameLanguage()));
        row.put("participants", d.getParticipants());
        row.put("teams", d.getTeams());
        row.put("winnerTeamIds", d.getWinnerTeamIds());
        row.put("winnerTeamNames", d.getWinnerTeamNames());
        row.put("winningScore", d.getWinningScore());
        row.put("totalScore", d.getTotalScore());
        row.put("wordCount", d.getWordCount());
        row.put("status", d.getStatus());
        row.put("startedAtMs", d.getStartedAtMs());
        row.put("finishedAtMs", d.getFinishedAtMs());
        row.put("expiresAtMs", d.getExpiresAtMs() == null ? 0 : d.getExpiresAtMs());
        row.put("savedCount", d.getSavedCount());
        row.put("sizeBytes", d.getSizeBytes());
        row.put("durationNs", d.getDurationNs());
        row.put("egressId", d.getEgressId() == null ? "" : d.getEgressId());
        row.put("error", d.getStartError() != null ? d.getStartError()
                : (d.getEgressError() == null ? "" : d.getEgressError()));
        row.put("secretWordRecorded", Boolean.TRUE.equals(d.getSecretWordRecorded()));
        row.put("recorderReadyAtMs", d.getRecorderReadyAtMs());
        row.put("recorderStartSignalAtMs", d.getRecorderStartSignalAtMs());
        row.put("egressActiveAtMs", d.getEgressActiveAtMs());
        row.put("prewarmed", Boolean.TRUE.equals(d.getPrewarmed()));
        row.put("terminationReason", d.getTerminationReason());
        return row;
    }

    /**
     * Подписанная ссылка живёт полчаса. HEAD перед выдачей намеренно не делаем:
     * это лишние платные обращения к хранилищу, а отсутствие файла всё равно
     * проявится на самом запросе.
     */
    @Override
    public Map<String, Object> signedUrls(GameRecording recording, String downloadName) {
        if (recording.getObjectPath() == null || recording.getObjectPath().isBlank()) {
            throw ApiException.of("RECORDING_NOT_READY", 409);
        }
        Duration ttl = Duration.ofMinutes(30);
        String cleanName = Json.str(downloadName).replaceAll("[\"\\\\]", "_");
        String watchUrl = storage.presignGet(recording.getObjectPath(), ttl, "video/mp4", null, null);
        String downloadUrl = storage.presignGet(recording.getObjectPath(), ttl, "video/mp4",
                "attachment; filename=\"" + cleanName + "\"", null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("watchUrl", watchUrl);
        result.put("downloadUrl", downloadUrl);
        result.put("expiresAtMs", System.currentTimeMillis() + ttl.toMillis());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> cleanupExpired(int limit) {
        long now = System.currentTimeMillis();
        List<GameRecording> expired = getterMedia.getExpiredRecordings(now, Math.max(1, Math.min(200, limit)));
        int deleted = 0;
        for (GameRecording recording : expired) {
            // Сохранённые кем-то записи не удаляются даже после истечения срока.
            if (recording.getSavedCount() > 0 || !recording.getSavedBy().isEmpty()) {
                continue;
            }
            if (recording.getObjectPath() != null && !recording.getObjectPath().isBlank()) {
                try {
                    storage.delete(recording.getObjectPath());
                } catch (RuntimeException e) {
                    log.warn("Не удалось удалить объект записи {}: {}", recording.getObjectPath(), e.getMessage());
                }
            }
            recording.setStatus("deleted");
            recording.setDeletedAtMs(now);
            recording.setObjectPath(null);
            recording.setExpiresAtMs(null);
            saverMedia.saveRecording(recording);
            deleted++;
        }
        return Map.of("checked", expired.size(), "deleted", deleted);
    }
}
