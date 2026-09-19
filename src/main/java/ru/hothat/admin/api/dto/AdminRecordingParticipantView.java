package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Участник записанной партии. */
@Schema(description = "Участник записанной партии")
public record AdminRecordingParticipantView(

        @Schema(description = "Игрок", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Ник на момент партии", example = "vasya")
        String nickname,

        @Schema(description = "Команда внутри партии; null — команду не сохранили",
                example = "team-a", nullable = true)
        String teamId,

        @Schema(description = "Это был тестовый бот, а не человек", example = "false", type = "boolean")
        boolean testBot) {
}
