package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Кадр обновления канала {@code /ws/v2/room/{roomId}}.
 *
 * <p>Уходит после того, как транзакция, изменившая комнату, зафиксирована, —
 * и только тогда. До фиксации стол показал бы отгаданное слово, которого при
 * откате не останется.
 *
 * <p>Кадр один на любое изменение: место, состав, сообщение, ход, диверсия.
 * Дробить его по предметам значило бы вернуть шесть подписок, только уже
 * внутри одного сокета.
 */
@Schema(description = "Кадр изменения комнаты")
public record RoomChannelEventDTO(

        @Schema(description = "Имя кадра", example = "room", allowableValues = "room")
        String type,

        @Schema(description = "Комната после изменения")
        RoomChannelView room) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public RoomChannelEventDTO(RoomChannelView room) {
        this("room", room);
    }
}
