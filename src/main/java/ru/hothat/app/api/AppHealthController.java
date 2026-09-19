package ru.hothat.app.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.app.api.dto.HealthResponseDTO;
import ru.hothat.app.usecase.CheckHealthUseCase;

/**
 * Проба живости сервиса.
 *
 * <p>Заменяет {@code GET /api/health} без блока {@code env}: тот перечислял
 * анонимному вызывающему, какие секреты настроены на стенде.
 *
 * <p>Отдельный класс, а не сосед фича-флага под общим «сведения о среде»:
 * аудитория и смысл разные. Сюда стучится балансировщик и агент времени
 * работы, ответ не кешируется никогда, а отказ означает «снимай узел из
 * ротации». Флаг спрашивает браузер, ответ живёт полминуты в кеше, а отказ
 * означает лишь «кнопку не покажем». Один класс на два таких вопроса — это и
 * есть та самая свалка под одним словом, только этажом ниже.
 *
 * <p>Старый адрес пока жив: мониторинг переедет на этот маршрут отдельно.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/app/health")
@Tag(name = "App · живость", description = "Проба живости сервиса")
@SecurityRequirements
public class AppHealthController {

    private final CheckHealthUseCase checkHealth;

    @Operation(summary = "Жив ли сервис",
            description = "Отвечает, пока жив процесс. Зависимости не проверяет намеренно: "
                    + "падение базы не должно выглядеть как падение сервиса.")
    @ApiResponse(responseCode = "200", description = "Процесс отвечает")
    @GetMapping
    public ResponseEntity<HealthResponseDTO> health() {
        return ResponseEntity.ok(checkHealth.run());
    }
}
