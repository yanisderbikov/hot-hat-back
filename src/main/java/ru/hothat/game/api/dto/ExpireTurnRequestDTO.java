package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Ход истёк.
 *
 * <p>Просьбу шлёт любой участник — у объясняющего мог закрыться браузер ровно
 * на последней секунде, и партия зависла бы навсегда. Поторопить ход этим
 * нельзя: время считает сервер, и до дедлайна ответ будет {@code NOT_YET}.
 */
@Schema(description = "Запрос на закрытие истёкшего хода")
public record ExpireTurnRequestDTO(

        @Schema(description = "Ход, который считают истёкшим", example = "turn_3f9a1c04b77e2d15")
        @NotBlank(message = "Не указан ход.")
        @Pattern(regexp = "^turn_[a-f0-9]{16}$", message = "Некорректный ход.")
        String turnId) {
}
