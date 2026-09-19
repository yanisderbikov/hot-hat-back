package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Записывать ли партии этой комнаты.
 *
 * <p>Включает только владелец сервиса и только до старта партии: запись стоит
 * денег у внешнего сервиса, а начатую партию дописать с середины нельзя.
 */
@Schema(description = "Настройка записи партий комнаты")
public record SetRecordingPreferenceRequestDTO(

        @Schema(description = "Записывать партии", example = "true", type = "boolean")
        @NotNull(message = "Не сказано, включать ли запись.")
        Boolean enabled) {
}
