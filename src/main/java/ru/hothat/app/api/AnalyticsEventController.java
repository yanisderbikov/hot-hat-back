package ru.hothat.app.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.app.api.dto.AnalyticsEventAcceptedResponseDTO;
import ru.hothat.app.api.dto.RecordAnalyticsEventRequestDTO;
import ru.hothat.app.usecase.RecordAnalyticsEventUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Приём клиентских событий для отчётов.
 *
 * <p>Заменяет {@code POST /api/analytics} ({@code app-core.js:910}), где тело
 * было {@code Map<String,Object>}, вид события — свободной строкой, а ответ
 * приходил кодом 201 в том числе на дубликат, которого никто не создавал.
 *
 * <p>Только для вошедших: гостю аналитика закрыта намеренно.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/app/analytics-events")
@Tag(name = "App · аналитика", description = "Приём клиентских событий")
@SecurityRequirement(name = "Bearer")
public class AnalyticsEventController {

    private final RecordAnalyticsEventUseCase recordAnalyticsEvent;

    @Operation(summary = "Сообщить о событии",
            description = "Идемпотентно по eventKey: обработчик конца партии срабатывает у каждого "
                    + "участника, и одно событие приезжает несколько раз.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Событие принято; повтор ничего не меняет"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: неизвестный вид события, "
                    + "пустой ключ или значение вне границ", content = @Content)})
    @PostMapping
    public ResponseEntity<AnalyticsEventAcceptedResponseDTO> record(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody RecordAnalyticsEventRequestDTO request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(recordAnalyticsEvent.run(user, request));
    }
}
