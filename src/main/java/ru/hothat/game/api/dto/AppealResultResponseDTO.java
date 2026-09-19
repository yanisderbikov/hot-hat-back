package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Итоги хода.
 *
 * <p>Отменённые слова возвращаются в шляпу, счёт команды уменьшается на их
 * число, и по окончательному счёту начисляются награды. Очко за слово даётся
 * сразу при засчитывании, поэтому апелляция только вычитает.
 */
@Schema(description = "Итоги хода после голосования")
public record AppealResultResponseDTO(

        @Schema(description = "Что произошло", example = "SETTLED")
        AppealClosingOutcome outcome,

        @Schema(description = "Окончательный счёт хода", example = "4")
        int finalScore,

        @Schema(description = "Отменённые слова", example = "[\"guess_8c1d0b4a\"]")
        List<String> invalidWordIds,

        @Schema(description = "Сколько слов вернулось в шляпу", example = "1")
        int returnedWordCount,

        @Schema(description = "Начисленные награды", nullable = true)
        MatchRewardsView rewards,

        @Schema(description = "Партия закончилась этим ходом: шляпа пуста", example = "false")
        boolean matchFinished,

        @Schema(description = "Состояние партии после подведения итогов")
        MatchStateView match) {
}
