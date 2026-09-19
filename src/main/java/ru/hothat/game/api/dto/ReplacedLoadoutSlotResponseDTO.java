package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог замены мема в слоте.
 *
 * <p>Повтор той же замены — не ошибка: {@code changed} равен {@code false},
 * обойма приходит та же. Клиент, нажавший дважды, не должен получать красный
 * тост за то, что добился желаемого.
 */
@Schema(description = "Итог замены мема")
public record ReplacedLoadoutSlotResponseDTO(

        @Schema(description = "Обойма изменилась", example = "true")
        boolean changed,

        @Schema(description = "Номер слота, 0..4", example = "2")
        int slotIndex,

        @Schema(description = "Какой мем стоял в слоте", example = "meme-1a2b3c4d5e6f")
        String previousMemeId,

        @Schema(description = "Какой мем стоит теперь", example = "meme-7b1c2d3e4f5a")
        String memeId,

        @Schema(description = "Снаряжение после замены")
        ArsenalView arsenal) {
}
