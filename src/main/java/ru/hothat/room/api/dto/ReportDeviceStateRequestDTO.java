package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Что игрок сообщает о своей камере и микрофоне.
 *
 * <p>Оба флага обязательны и присылаются вместе: «камера включена» без
 * «микрофон выключен» — это половина состояния, и соседи по столу увидели бы
 * у человека то, чего он не включал. До сих пор их писали три разных места
 * фронтенда независимо друг от друга ({@code app-core.js:5093},
 * {@code :12499}, {@code livekit.js:3175}).
 */
@Schema(description = "Состояние камеры и микрофона игрока")
public record ReportDeviceStateRequestDTO(

        @Schema(description = "Передаётся ли видео", example = "true", type = "boolean")
        @NotNull(message = "Не сказано, включена ли камера.")
        Boolean cameraEnabled,

        @Schema(description = "Передаётся ли звук", example = "true", type = "boolean")
        @NotNull(message = "Не сказано, включён ли микрофон.")
        Boolean microphoneEnabled,

        @Schema(description = "Чем ведётся видеосвязь; сейчас всегда livekit",
                example = "livekit", maxLength = 24, nullable = true)
        @Size(max = 24, message = "Некорректный поставщик видеосвязи.")
        String videoProvider) {
}
