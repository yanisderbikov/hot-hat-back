package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Итог передачи очереди. */
@Schema(description = "Итог передачи очереди")
public record NextTurnResponseDTO(

        @Schema(description = "Что произошло", example = "ADVANCED")
        TurnAdvanceOutcome outcome,

        @Schema(description = "Чья теперь очередь", example = "team_1e5f8c3a", nullable = true)
        String currentTeamId,

        @Schema(description = "Состояние партии")
        MatchStateView match) {
}
