package ru.hothat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.hothat.config.ApiException;
import ru.hothat.service.cleanup.RoomCleanupService;
import ru.hothat.service.recording.RecordingService;
import ru.hothat.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Уборка по внешнему расписанию.
 *
 * <p>Ветка вошедшего пользователя осталась от времён, когда клиент чистил свою
 * комнату при выходе; сегодня по этому адресу приходит только планировщик с
 * CRON_SECRET — браузер закрывает комнату сценарием {@code /api/v2/room}.
 *
 * <p>Наследники в новой поверхности есть:
 * {@code POST /api/v2/machine/maintenance/room-sweeps} и
 * {@code /recording-sweeps}. Адрес всё же оставлен: расписание живёт во
 * внешней настройке, которую мы не выкатываем, и снос контроллера раньше её
 * переключения означал бы, что уборка молча перестала ходить.
 */
@Deprecated(forRemoval = true)
@RequiredArgsConstructor
@RestController
@Tag(name = "Cleanup", description = "Уборка комнат и записей")
public class CleanupController {

    private final RoomCleanupService roomCleanupService;
    private final RecordingService recordingService;

    @Value("${hot-hat.cron-secret:}")
    private String cronSecret;

    @Operation(summary = "Убрать брошенные комнаты")
    @RequestMapping(value = "/api/cleanup-rooms", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> cleanupRooms(
            Authentication auth,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> raw) {
        boolean cron = cronAuthorized(authorization);
        if (!cron) {
            CurrentUser.of(auth);
        }
        Map<String, Object> body = raw == null ? Map.of() : raw;
        String roomId = cleanRoomId(Json.str(body.get("room_id")));
        boolean shouldSweep = cron || Json.bool(body.get("sweep"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("target", roomId.isEmpty() ? null : roomCleanupService.cleanupOne(roomId));
        result.put("sweep", shouldSweep ? roomCleanupService.sweep() : null);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Удалить записи с истёкшим сроком")
    @RequestMapping(value = "/api/cleanup-recordings", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> cleanupRecordings(
            Authentication auth,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!cronAuthorized(authorization)) {
            CurrentUser.admin(auth);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.putAll(recordingService.cleanupExpired(100));
        return ResponseEntity.ok(result);
    }

    private boolean cronAuthorized(String authorization) {
        return cronSecret != null && !cronSecret.isBlank()
                && ("Bearer " + cronSecret).equals(authorization);
    }

    private static String cleanRoomId(String value) {
        String id = value == null ? "" : value.trim();
        if (id.isEmpty() || id.length() > 120 || !id.matches("^[a-zA-Z0-9_-]+$")) {
            throw id.isEmpty() ? new NoRoomRequested() : ApiException.of("ROOM_INVALID", 400);
        }
        return id;
    }

    /** Отсутствие room_id — не ошибка: запрос мог быть только на общий проход. */
    private static final class NoRoomRequested extends RuntimeException {
    }

    @ExceptionHandler(NoRoomRequested.class)
    ResponseEntity<Map<String, Object>> noRoom() {
        return ResponseEntity.ok(Map.of("ok", true, "target", Map.of(), "sweep", Map.of()));
    }
}
