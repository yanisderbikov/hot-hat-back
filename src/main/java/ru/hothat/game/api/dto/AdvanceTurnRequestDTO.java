package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Передать очередь следующей команде.
 *
 * <p>Порядок команд считает сервер: в браузере его считал тот, кто нажал
 * кнопку, и два одновременных нажатия перескакивали через команду.
 * Идемпотентно по {@code turnId} закончившегося хода.
 */
@Schema(description = "Запрос на передачу очереди")
public record AdvanceTurnRequestDTO(

        @Schema(description = "Ход, после которого передают очередь", example = "turn_3f9a1c04b77e2d15")
        @NotBlank(message = "Не указан ход.")
        @Pattern(regexp = "^turn_[a-f0-9]{16}$", message = "Некорректный ход.")
        String turnId) {
}
