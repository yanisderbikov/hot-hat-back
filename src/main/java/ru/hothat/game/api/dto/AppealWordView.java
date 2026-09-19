package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Слово, вынесенное на голосование.
 *
 * <p>Голоса видны всем и сразу: голосование открытое, десять секунд, и скрывать
 * счёт незачем — он и есть предмет разговора за столом.
 */
@Schema(description = "Спорное слово хода")
public record AppealWordView(

        @Schema(description = "Идентификатор слова в ходе", example = "guess_8c1d0b4a")
        String wordId,

        @Schema(description = "Само слово", example = "Абажур")
        String word,

        @Schema(description = "Сколько голосов подано за отмену", example = "2")
        int votesToCancel,

        @Schema(description = "Отменил ли это слово текущий участник", example = "true")
        boolean myVote) {
}
