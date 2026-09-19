package ru.hothat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.hothat.service.monitor.MonitorService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Мониторинг ресурсов по внешнему расписанию.
 *
 * <p>Обе админские ветки переехали:
 * {@code GET /api/v2/admin/usage-snapshots} и {@code POST} туда же — их
 * страница администратора и зовёт. Здесь остался вход по секрету
 * {@code X-Hot-Hat-Monitor-Secret}, которым пользуется агент на хосте, и его
 * наследник {@code POST /api/v2/machine/usage-snapshots}.
 *
 * <p>Адрес оставлен по той же причине, что и уборка: настройку агента мы не
 * выкатываем, и до её переключения снос означал бы, что метрики перестали
 * приходить — молча, потому что писать их некому.
 */
@Deprecated(forRemoval = true)
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/monitor")
@Tag(name = "Monitor", description = "Расход ресурсов")
public class MonitorController {

    private final MonitorService monitorService;

    @Value("${monitor.secret:}")
    private String monitorSecret;

    @Operation(summary = "История снимков")
    @GetMapping
    public ResponseEntity<Map<String, Object>> history(Authentication auth,
                                                       @RequestParam(value = "start", required = false) String start,
                                                       @RequestParam(value = "end", required = false) String end) {
        CurrentUser.admin(auth);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.putAll(monitorService.history(start, end));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Снять новый снимок или отправить счётчик обращений")
    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(
            Authentication auth,
            @RequestHeader(value = "X-Hot-Hat-Monitor-Secret", required = false) String secret,
            @RequestBody(required = false) Map<String, Object> raw) {
        Map<String, Object> body = raw == null ? Map.of() : raw;
        if (secretOk(secret)) {
            if (!monitorService.scheduledWindowAllowed()) {
                return ResponseEntity.ok(Map.of("ok", true, "skipped", true, "reason", "scheduled_window"));
            }
            return ResponseEntity.ok(monitorService.collectAndPersist(true));
        }
        CurrentUser.admin(auth);
        return ResponseEntity.ok(monitorService.collectAndPersist(false));
    }

    private boolean secretOk(String provided) {
        return monitorSecret != null && !monitorSecret.isBlank()
                && provided != null && monitorSecret.equals(provided);
    }
}
