package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Итог пропуска слова. */
@Schema(description = "Итог пропуска слова")
public record SkippedWordResponseDTO(

        @Schema(description = "Что произошло", example = "SKIPPED")
        WordActionOutcome outcome,

        @Schema(description = "Пропущенное слово", example = "Абажур", nullable = true)
        String word,

        @Schema(description = "Следующее слово; пусто, если ход закрылся", example = "Водопад", nullable = true)
        String nextWord,

        @Schema(description = "Счёт хода: пропуск его не меняет", example = "4")
        int turnScore,

        @Schema(description = "Сколько слов осталось в шляпе вместе с текущим", example = "37")
        int wordsLeft,

        @Schema(description = "Ход закрылся этим действием", example = "false")
        boolean turnClosed,

        @Schema(description = "Когда закроется голосование; ноль, если ход не закрыт", example = "0")
        long appealEndsAtMs,

        @Schema(hidden = true)
        String nextOnGuess,

        @Schema(hidden = true)
        String nextOnSkip,

        /** Visible only to the current explainer (or in their own word result). */
        @Schema(hidden = true)
        java.util.List<String> privateDrawBag,

        /** Reserved roll for the next draw; together with privateDrawBag
         * supports zero-roundtrip consecutive word taps. */
        @Schema(hidden = true)
        Integer privateNextWordRoll) {
}
