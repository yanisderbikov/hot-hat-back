package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Комната, которую снёс прогон уборки, и почему. */
@Schema(description = "Убранная комната")
public record SweptRoomView(

        @Schema(description = "Идентификатор комнаты", example = "hat-3f1c9a2b7d4e6501")
        String roomId,

        @Schema(description = "Почему комната признана брошенной", example = "stale-10m",
                allowableValues = {"no-human-players", "all-humans-left", "stale-10m", "abandoned-game"})
        String reason) {
}
