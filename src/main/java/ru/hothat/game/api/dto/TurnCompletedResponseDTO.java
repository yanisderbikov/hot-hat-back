package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Итог завершения хода. */
@Schema(description = "Итог завершения хода")
public record TurnCompletedResponseDTO(

        @Schema(description = "Что произошло", example = "CLOSED")
        TurnClosingOutcome outcome,

        @Schema(description = "Предварительный счёт хода: апелляция может его уменьшить", example = "5")
        int preliminaryScore,

        @Schema(description = "Голосование по спорным словам", nullable = true)
        AppealView appeal,

        @Schema(description = "Состояние партии после закрытия хода")
        MatchStateView match) {
}
