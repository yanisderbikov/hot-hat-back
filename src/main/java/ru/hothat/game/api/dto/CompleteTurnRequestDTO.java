package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Завершить свой ход.
 *
 * <p>Идемпотентно по {@code turnId}: двойное нажатие или повтор после потери
 * связи закрывают тот же самый ход один раз. Без этого второй запрос закрыл бы
 * уже следующий ход другой команды — ровно тот дефект, который план называет
 * риском 2.
 */
@Schema(description = "Запрос на завершение хода")
public record CompleteTurnRequestDTO(

        @Schema(description = "Ход, который завершают", example = "turn_3f9a1c04b77e2d15")
        @NotBlank(message = "Не указан ход.")
        @Pattern(regexp = "^turn_[a-f0-9]{16}$", message = "Некорректный ход.")
        String turnId) {
}
