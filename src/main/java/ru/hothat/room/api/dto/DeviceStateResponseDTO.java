package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Состояние устройств, записанное сервером.
 *
 * <p>Отметка времени — серверная, и в этом весь смысл ответа: соседи по столу
 * решают по ней, чьё состояние свежее, а часы участников расходятся на минуты.
 */
@Schema(description = "Записанное состояние камеры и микрофона")
public record DeviceStateResponseDTO(

        @Schema(description = "Передаётся ли видео", example = "true", type = "boolean")
        boolean cameraEnabled,

        @Schema(description = "Передаётся ли звук", example = "true", type = "boolean")
        boolean microphoneEnabled,

        @Schema(description = "Когда состояние приняли, миллисекунды эпохи по серверным часам",
                example = "1788600000000", type = "integer")
        long mediaReadyAtMs) {
}
