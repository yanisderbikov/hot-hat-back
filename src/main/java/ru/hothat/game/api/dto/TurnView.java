package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Текущий ход.
 *
 * <p>Общая проекция: она включена и в состояние партии, и в ответы операций
 * хода. Форма описана один раз — иначе «что такое ход» пришлось бы повторить
 * в семи ответах, и седьмой неизбежно разошёлся бы с первым.
 *
 * <p>{@code currentWord} заполнено только для объясняющего. Для всех
 * остальных — включая зрителей и того, кто угадывает, — поле пусто: иначе
 * ответ сервера выдал бы слово тому, кто должен его отгадать.
 */
@Schema(description = "Текущий ход партии")
public record TurnView(

        @Schema(description = "Идентификатор хода: его же присылают операции хода для защиты от повтора",
                example = "turn_3f9a1c04b77e2d15")
        String turnId,

        @Schema(description = "Кто объясняет", example = "kZ8Qw1nBv2mX3cL4aS5dF6gH7jK8")
        String explainerUid,

        @Schema(description = "Имя объясняющего, замороженное на старте партии", example = "Аня")
        String explainerName,

        @Schema(description = "Кто угадывает", example = "pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m")
        String guesserUid,

        @Schema(description = "Имя угадывающего", example = "Борис")
        String guesserName,

        @Schema(description = "Когда ход кончится, миллисекунды эпохи по серверным часам", example = "1757150400000")
        long deadlineMs,

        @Schema(description = "Длительность этого хода в секундах; после паузы равна остатку", example = "60.0")
        double durationSeconds,

        @Schema(description = "Сколько слов команда угадала за этот ход", example = "4")
        int score,

        @Schema(description = "Сколько слов осталось в шляпе вместе с текущим", example = "37")
        int wordsLeft,

        @Schema(description = "Слово на руках; заполнено только для объясняющего", example = "Абажур",
                nullable = true)
        String currentWord) {
}
