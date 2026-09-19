package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Закрытая комната.
 *
 * <p>Ответ не пустой, хотя действие разрушительное: администратор должен
 * увидеть, что именно закрылось и сколько человек он при этом выставил.
 * Повторное закрытие уже закрытой комнаты отвечает тем же самым — это
 * идемпотентная операция, а не конфликт.
 */
@Schema(description = "Комната, закрытая администратором")
public record AdminRoomClosureResponseDTO(

        @Schema(description = "Комната", example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Фаза после закрытия", example = "closed")
        String phase,

        @Schema(description = "Кто закрыл", example = "Ab1cDeFgHiJkLmNoPqRsTuVwXyZ0")
        String closedByUid,

        @Schema(description = "Когда закрыли", example = "1788600000000", type = "integer")
        long closedAtMs,

        @Schema(description = "Сохранённая причина", example = "Жалобы участников")
        String reason,

        @Schema(description = "Сколько участников сидело в комнате на момент закрытия",
                example = "6", type = "integer")
        int playersAtClosure) {
}
