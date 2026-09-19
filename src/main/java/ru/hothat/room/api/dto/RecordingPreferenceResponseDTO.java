package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Записанная настройка записи партий. */
@Schema(description = "Настройка записи партий комнаты")
public record RecordingPreferenceResponseDTO(

        @Schema(description = "Комната", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Записывать ли партии", example = "true", type = "boolean")
        boolean enabled,

        @Schema(description = "Кто менял настройку", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String updatedByUid,

        @Schema(description = "Когда меняли, миллисекунды эпохи", example = "1788600000000",
                type = "integer")
        long updatedAtMs) {
}
