package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Первый кадр канала {@code /ws/v2/room/{roomId}}.
 *
 * <p>Приезжает сразу после рукопожатия и уже несёт всю комнату: иначе экран
 * был бы обязан сходить ещё и по HTTP за четырьмя ответами, чтобы нарисовать
 * стол, и до них показывать пустоту.
 */
@Schema(description = "Кадр открытия канала комнаты")
public record RoomChannelHelloDTO(

        @Schema(description = "Имя кадра", example = "hello", allowableValues = "hello")
        String type,

        @Schema(description = "Комната на момент открытия канала")
        RoomChannelView room) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public RoomChannelHelloDTO(RoomChannelView room) {
        this("hello", room);
    }
}
