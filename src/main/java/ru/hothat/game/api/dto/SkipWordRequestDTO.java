package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Слово пропущено.
 *
 * <p>Пропуск возвращает слово в шляпу и очков не приносит, но идентификатор
 * ему нужен ровно так же, как угаданному: без него повтор запроса после
 * потери связи прокрутил бы шляпу ещё на одно слово, и объясняющий потерял бы
 * подсказку ни за что.
 */
@Schema(description = "Запрос на пропуск слова")
public record SkipWordRequestDTO(

        @Schema(description = "Ход, в котором нажали кнопку", example = "turn_3f9a1c04b77e2d15")
        @NotBlank(message = "Не указан ход.")
        @Pattern(regexp = "^turn_[a-f0-9]{16}$", message = "Некорректный ход.")
        String turnId) {
}
