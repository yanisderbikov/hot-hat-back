package ru.hothat.service.recording;

import ru.hothat.config.HotHatUser;
import ru.hothat.model.media.GameRecording;

import java.util.Map;

/** Запись партии через LiveKit Egress и её жизненный цикл в S3. */
public interface RecordingService {

    long RECORDING_TTL_MS = 30L * 24 * 60 * 60 * 1000;

    String recordingId(String roomId, int gameNumber);

    String objectPath(String roomId, int gameNumber);

    /** Подпись ссылки, по которой headless-рекордер открывает страницу игры. */
    String viewSignature(String roomId, int gameNumber);

    boolean verifyViewSignature(String roomId, int gameNumber, String signature);

    String egressWebhookSignature(String roomId, int gameNumber);

    boolean verifyEgressWebhookSignature(String roomId, int gameNumber, String signature);

    Map<String, Object> startRoomRecording(HotHatUser user, String roomId, Integer gameNumber);

    Map<String, Object> finishRoomRecording(HotHatUser user, String roomId, Integer gameNumber);

    /** Завершение без участника: техническое поражение, конец церемонии, уборка. */
    Map<String, Object> finishRoomRecordingSystem(String roomId, Integer gameNumber, String terminationReason);

    Map<String, Object> applyEgressWebhook(String roomId, int gameNumber, Map<String, Object> event);

    /** Подтягивает статус зависшей записи из LiveKit и S3. */
    GameRecording refresh(GameRecording recording);

    Map<String, Object> publicRow(GameRecording recording);

    Map<String, Object> signedUrls(GameRecording recording, String downloadName);

    Map<String, Object> cleanupExpired(int limit);

    void markRecorderReady(String roomId, int gameNumber, String phase, String identity);

    void markRecorderStarted(String roomId, int gameNumber, String phase, String identity);

    /** URL страницы-рекордера с токеном скрытого участника LiveKit. */
    String recorderPageUrl(String roomId, int gameNumber);
}
