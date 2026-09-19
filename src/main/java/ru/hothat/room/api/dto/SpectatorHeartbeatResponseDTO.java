package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отметка присутствия зрителя.
 *
 * <p>Отдельная запись, а не общая с игроцкой: у зрителя другое окно живости
 * (семь минут против пяти), и отдавать его в поле с тем же именем из общей
 * схемы значило бы утверждать, что пороги совпадают.
 */
@Schema(description = "Принятая отметка присутствия зрителя")
public record SpectatorHeartbeatResponseDTO(

        @Schema(description = "Записанная отметка, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long lastSeenAtMs,

        @Schema(description = "Серверное время на момент ответа", example = "1788600000000",
                type = "integer")
        long serverTimeMs,

        @Schema(description = "Сколько отметка зрителя считается свежей, миллисекунды",
                example = "420000", type = "integer")
        long aliveWindowMs) {
}
