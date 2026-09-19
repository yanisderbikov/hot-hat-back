package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Полное зеркало комнаты — запасной путь рекордера.
 *
 * <p>Заменяет режим {@code GET /api/recording-state?state=1}. Страница берёт
 * его, только если живое зеркало по сокету не поднялось
 * ({@code app-core.js:14083}); при нормальной работе адрес не зовётся вовсе.
 */
@Schema(description = "Состояние комнаты целиком для рекордера")
public record RecorderRoomStateResponseDTO(

        @Schema(description = "Совпала ли снимаемая партия с текущей партией комнаты")
        RecorderSceneState state,

        @Schema(description = "Комната съёмки", example = "hat-3f1c9a2b7d4e6501")
        String roomId,

        @Schema(description = "Номер снимаемой партии", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Фаза комнаты", example = "active",
                allowableValues = {"setup", "turnIntro", "active", "appeal", "between", "finished", "closed"})
        String phase,

        @Schema(description = "Состояние комнаты; null при state=STALE_GAME", nullable = true)
        RecorderRoomStateView roomState,

        @Schema(description = "Серверное время, мс эпохи",
                example = "1757068800000", type = "integer", format = "int64")
        long serverNowMs) {
}
