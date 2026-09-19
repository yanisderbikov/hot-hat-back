package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Хозяин подтверждает, что он ещё за экраном.
 *
 * <p>Отсчёт бездействия идёт только при полном составе, поэтому отметка
 * дорога: пропустить её на третьей минуте значит отдать комнату следующему.
 * Клиент шлёт её не чаще раза в тридцать секунд и обязательно — когда до
 * передачи остаётся меньше сорока пяти ({@code app-core.js:14427}).
 */
@Schema(description = "Отметка активности хозяина")
public record ReportHostActivityRequestDTO(

        @Schema(description = "Чем подтверждена активность", example = "typing")
        @NotNull(message = "Не сказано, чем подтверждена активность.")
        HostActivityKind kind) {
}
