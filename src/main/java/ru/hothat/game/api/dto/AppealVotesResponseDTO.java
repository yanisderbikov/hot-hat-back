package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Расклад голосования после поданного голоса.
 *
 * <p>В ответ едет весь расклад, а не записанное значение: по нему рисуется
 * строка «за отмену 2 из 3», и по одному своему голосу её не собрать.
 */
@Schema(description = "Итог подачи голоса")
public record AppealVotesResponseDTO(

        @Schema(description = "Слова, за отмену которых голосует этот участник",
                example = "[\"guess_8c1d0b4a\"]")
        List<String> myVotes,

        @Schema(description = "Сколько голосов подано за это слово", example = "2")
        int votesForWord,

        @Schema(description = "Голосование целиком")
        AppealView appeal) {
}
