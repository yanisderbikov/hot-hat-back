package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Подвести итог голосования.
 *
 * <p>Просьбу шлют все участники разом, как только истекли десять секунд, —
 * и это нормально: подводит итог первый, остальные получают
 * {@code ALREADY_SETTLED}. Без идемпотентности по {@code turnId} второй запрос
 * начислил бы награды повторно.
 */
@Schema(description = "Запрос на подведение итогов хода")
public record CloseAppealRequestDTO(

        @Schema(description = "Ход, по которому шло голосование", example = "turn_3f9a1c04b77e2d15")
        @NotBlank(message = "Не указан ход.")
        @Pattern(regexp = "^turn_[a-f0-9]{16}$", message = "Некорректный ход.")
        String turnId) {
}
