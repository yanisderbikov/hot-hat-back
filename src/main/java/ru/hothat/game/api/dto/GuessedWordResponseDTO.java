package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Слово засчитано.
 *
 * <p>Следующее слово приезжает тем же ответом: раньше клиент тянул его сам из
 * своей копии шляпы, а теперь ждать его отдельным запросом значило бы держать
 * объясняющего перед пустым экраном лишний круг по сети.
 */
@Schema(description = "Итог засчитывания слова")
public record GuessedWordResponseDTO(

        @Schema(description = "Что произошло", example = "COUNTED")
        WordActionOutcome outcome,

        @Schema(description = "Разобранное слово", example = "Абажур", nullable = true)
        String word,

        @Schema(description = "Следующее слово; пусто, если ход закрылся", example = "Водопад", nullable = true)
        String nextWord,

        @Schema(description = "Счёт хода после засчитывания", example = "5")
        int turnScore,

        @Schema(description = "Сколько слов осталось в шляпе вместе с текущим", example = "36")
        int wordsLeft,

        @Schema(description = "Ход закрылся этим действием: слова кончились или вышло время", example = "false")
        boolean turnClosed,

        @Schema(description = "Когда закроется голосование; ноль, если ход не закрыт", example = "0")
        long appealEndsAtMs) {
}
