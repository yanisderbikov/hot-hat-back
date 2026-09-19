package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Кто теперь хозяин комнаты.
 *
 * <p>Имя нового хозяина едет вместе с идентификатором, потому что показать его
 * надо немедленно — «комната перешла к Пете», — а вытаскивать имя из состава
 * значило бы дождаться следующего снимка.
 */
@Schema(description = "Хозяин комнаты")
public record RoomHostResponseDTO(

        @Schema(description = "Комната", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Новый хозяин", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
        String hostUid,

        @Schema(description = "Как зовут нового хозяина в этой комнате", example = "petya")
        String hostName,

        @Schema(description = "Кто передал комнату", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String transferredByUid,

        @Schema(description = "Когда передали, миллисекунды эпохи", example = "1788600000000",
                type = "integer")
        long transferredAtMs) {
}
