package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Первый кадр канала {@code /ws/v2/machine/recorder/rooms/{roomId}}.
 *
 * <p>Приезжает сразу после рукопожатия и уже несёт сцену: страница записи
 * начинает снимать по готовности, и лишний поход по HTTP за первым кадром
 * стоил бы секунды видео.
 */
@Schema(description = "Кадр открытия канала рекордера")
public record RecorderChannelHelloDTO(

        @Schema(description = "Имя кадра", example = "hello", allowableValues = "hello")
        String type,

        @Schema(description = "Сцена на момент открытия канала")
        RecorderMirrorView mirror) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public RecorderChannelHelloDTO(RecorderMirrorView mirror) {
        this("hello", mirror);
    }
}
