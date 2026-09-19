package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Закрытый ход — то, что показывает экран между ходами.
 *
 * <p>Переживает сам ход: пока идёт голосование и потом, до следующего
 * {@code POST …/turn}, поле {@code turn} уже пусто, а «Красные: 4» на экране
 * итогов рисовать по-прежнему надо ({@code app-core.js:11739}).
 *
 * <p>Два счёта, а не один: {@code score} — сколько засчитали в ходе,
 * {@code finalScore} — сколько осталось после апелляции. Пока итог не подведён,
 * второго нет, и экран показывает первый.
 */
@Schema(description = "Закрытый ход")
public record LastTurnView(

        @Schema(description = "Идентификатор закрытого хода", example = "turn_3f9a1c04b77e2d15")
        String turnId,

        @Schema(description = "Чья команда ходила", example = "team_7d2c9a1b")
        String teamId,

        @Schema(description = "Сколько слов засчитали в ходе, до апелляции", example = "4")
        int score,

        @Schema(description = "Сколько осталось после апелляции; null — итог ещё не подведён",
                example = "3", nullable = true)
        Integer finalScore) {
}
