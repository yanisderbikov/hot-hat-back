package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отметка «я сейчас в этой комнате» после записи.
 *
 * <p>Возвращает время сервера, а не подтверждение «ок». Метку ставит браузер
 * своими часами ({@code app-core.js}: {@code activeRoomUpdatedAt: Date.now()}),
 * и по разошедшимся часам нельзя было понять, свежая метка или недельной
 * давности. Теперь время ставит сервер и сразу его называет.
 */
@Schema(description = "Отметка о комнате игрока")
public record ActiveRoomResponseDTO(

        @Schema(description = "Комната, отмеченная как текущая", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Когда сервер поставил метку, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long markedAtMs) {
}
