package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Подтверждение готовности рекордера. */
@Schema(description = "Готовность рекордера принята")
public record RecorderReadySignalResponseDTO(

        @Schema(description = "Комната съёмки", example = "hat-3f1c9a2b7d4e6501")
        String roomId,

        @Schema(description = "Номер снимаемой партии", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Фаза комнаты в момент сигнала", example = "setup",
                allowableValues = {"setup", "turnIntro", "active", "appeal", "between", "finished", "closed"})
        String phase,

        @Schema(description = "Личность рекордера в LiveKit, которую запомнил сервер",
                example = "hot-hat-recorder-3f1c9a2b", nullable = true)
        String recorderLivekitIdentity,

        @Schema(description = "Когда сигнал принят, мс эпохи",
                example = "1757068800000", type = "integer", format = "int64")
        long acknowledgedAtMs) {
}
