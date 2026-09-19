package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Кадр обновления канала {@code /ws/v2/machine/recorder/rooms/{roomId}}.
 *
 * <p>Заменяет две подписки зеркала рекордера ({@code app-core.js:13913},
 * {@code :13940}). Уходит после фиксации транзакции: записанное видео нельзя
 * переснять, и кадр с состоянием, которого при откате не станет, остался бы в
 * файле навсегда.
 */
@Schema(description = "Кадр изменения сцены")
public record RecorderChannelEventDTO(

        @Schema(description = "Имя кадра", example = "mirror", allowableValues = "mirror")
        String type,

        @Schema(description = "Сцена после изменения")
        RecorderMirrorView mirror) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public RecorderChannelEventDTO(RecorderMirrorView mirror) {
        this("mirror", mirror);
    }
}
