package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Слово угадано.
 *
 * <p>{@code turnId} обязателен: между нажатием и приходом запроса ход мог
 * закончиться и начаться новый — у другой команды и с другим словом. Без
 * сверки очко ушло бы не туда. Несовпадение — 409 {@code TURN_STALE}: перечитать
 * состояние и не повторять.
 *
 * <p>Само слово в запросе не называется. Оно на сервере, и присланное клиентом
 * означало бы, что засчитать можно любое.
 */
@Schema(description = "Запрос на засчитывание слова")
public record GuessWordRequestDTO(

        @Schema(description = "Ход, в котором нажали кнопку", example = "turn_3f9a1c04b77e2d15")
        @NotBlank(message = "Не указан ход.")
        @Pattern(regexp = "^turn_[a-f0-9]{16}$", message = "Некорректный ход.")
        String turnId) {
}
