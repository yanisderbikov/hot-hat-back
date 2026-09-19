package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Итог закрытия истёкшего хода. */
@Schema(description = "Итог закрытия истёкшего хода")
public record TurnExpiredResponseDTO(

        @Schema(description = "Что произошло", example = "CLOSED")
        TurnClosingOutcome outcome,

        @Schema(description = "Сколько миллисекунд хода ещё оставалось; ноль, если ход закрыт", example = "0")
        long remainingMs,

        @Schema(description = "Голосование по спорным словам", nullable = true)
        AppealView appeal,

        @Schema(description = "Состояние партии")
        MatchStateView match) {
}
