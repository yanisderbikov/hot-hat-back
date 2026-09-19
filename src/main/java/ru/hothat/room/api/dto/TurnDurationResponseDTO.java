package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Записанная длительность хода. */
@Schema(description = "Длительность хода")
public record TurnDurationResponseDTO(

        @Schema(description = "Комната", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Сколько секунд длится ход", example = "60", type = "integer")
        int turnDurationSeconds) {
}
