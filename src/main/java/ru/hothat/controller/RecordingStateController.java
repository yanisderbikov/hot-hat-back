package ru.hothat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.hothat.config.ApiException;
import ru.hothat.service.recording.RecordingService;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Вебхук LiveKit Egress. Защищён подписью на секрете LiveKit, а не
 * пользовательским токеном: аккаунта у вызывающего нет.
 *
 * <p>Состояние комнаты для рекордера отсюда убрано: страница записи слушает
 * канал {@code /ws/v2/machine/recorder/rooms/{roomId}}, а по HTTP то же самое
 * отдают адреса {@code /api/v2/machine/recorder/**}.
 *
 * <p>Сам вебхук оставлен намеренно, хотя наследник у него есть —
 * {@code POST /api/v2/machine/webhooks/livekit-egress/rooms/{roomId}/games/{n}}.
 * Обратный вызов делает LiveKit, а не мы, и переключить его — отдельная
 * выкатка: до неё выгрузки, начатые старым кодом, стучатся сюда.
 */
@Deprecated(forRemoval = true)
@AllArgsConstructor
@RestController
@Tag(name = "Recorder", description = "Состояние для записи партии")
public class RecordingStateController {

    private final RecordingService recordingService;

    @Operation(summary = "Вебхук LiveKit Egress")
    @PostMapping("/api/recording-egress")
    public ResponseEntity<Map<String, Object>> egressWebhook(@RequestParam Map<String, String> query,
                                                             @RequestBody(required = false) Map<String, Object> raw) {
        String roomId = cleanRoomId(query.get("roomId"));
        int gameNumber = (int) Math.max(0, Json.num(query.get("gameNumber")));
        if (!recordingService.verifyEgressWebhookSignature(roomId, gameNumber, query.get("sig"))) {
            throw ApiException.of("RECORDING_WEBHOOK_FORBIDDEN", 403);
        }
        Map<String, Object> body = raw == null ? Map.of() : raw;
        String eventName = Json.str(body.get("event"));
        if (!eventName.startsWith("egress_")) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", true));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("event", eventName);
        result.putAll(recordingService.applyEgressWebhook(roomId, gameNumber, body));
        return ResponseEntity.ok(result);
    }

    private static String cleanRoomId(String value) {
        String roomId = value == null ? "" : value.trim();
        if (!Ids.ROOM.matcher(roomId).matches()) {
            throw ApiException.of("ROOM_INVALID", 400);
        }
        return roomId;
    }
}
