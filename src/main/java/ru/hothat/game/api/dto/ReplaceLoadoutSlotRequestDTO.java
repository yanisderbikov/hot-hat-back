package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Заменить мем в слоте посреди партии.
 *
 * <p>Менять можно в любой момент, кроме собственного хода: активная команда
 * обойму не трогает — иначе она подбирала бы ролик под то, что происходит на
 * сцене прямо сейчас.
 */
@Schema(description = "Замена мема в слоте")
public record ReplaceLoadoutSlotRequestDTO(

        @Schema(description = "Какой мем зарядить вместо стоящего в слоте", example = "meme-7b1c2d3e4f5a")
        @NotBlank(message = "Не указан мем.")
        String memeId) {
}
