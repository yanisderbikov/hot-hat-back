package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Первый кадр канала {@code /ws/v2/conference/{id}}.
 *
 * <p>Приезжает сразу после рукопожатия и уже несёт состав и ленту: экрану
 * созвона не нужно ходить за ними по HTTP.
 */
@Schema(description = "Кадр открытия канала видео-чата")
public record ConferenceChannelHelloDTO(

        @Schema(description = "Имя кадра", example = "hello", allowableValues = "hello")
        String type,

        @Schema(description = "Видео-чат на момент открытия канала")
        ConferenceChannelView conference) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public ConferenceChannelHelloDTO(ConferenceChannelView conference) {
        this("hello", conference);
    }
}
