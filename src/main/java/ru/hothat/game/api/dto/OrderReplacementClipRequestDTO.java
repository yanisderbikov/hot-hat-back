package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import ru.hothat.common.validation.PlayerUid;

/**
 * Заказать съёмку Подмены.
 *
 * <p>Снимаемого называет заказчик, хотя сервер и сам знает, кто объясняет.
 * Причина та же, что у {@code turnId} в операциях хода: между нажатием и
 * приходом запроса ход мог смениться, и молчаливая съёмка сняла бы не того
 * человека. Несовпадение — 409 {@code REPLACEMENT_WRONG_TARGET}.
 */
@Schema(description = "Запрос на съёмку клипа Подмены")
public record OrderReplacementClipRequestDTO(

        @Schema(description = "Кого снимают: тот, кто объясняет прямо сейчас",
                example = "kZ8Qw1nBv2mX3cL4aS5dF6gH7jK8")
        @NotBlank(message = "Не указан игрок.")
        @PlayerUid
        String targetUid) {
}
