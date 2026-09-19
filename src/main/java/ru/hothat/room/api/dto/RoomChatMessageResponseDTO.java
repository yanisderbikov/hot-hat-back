package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отправленное сообщение чата комнаты.
 *
 * <p>В ответе — то же сообщение, что придёт всем остальным каналом комнаты.
 * Отправитель показывает своё сразу и по идентификатору узнаёт собственное
 * эхо, когда оно вернётся из канала.
 */
@Schema(description = "Отправленное сообщение чата")
public record RoomChatMessageResponseDTO(

        @Schema(description = "Созданное сообщение")
        RoomChatMessageView message) {
}
