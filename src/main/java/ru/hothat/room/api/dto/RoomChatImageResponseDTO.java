package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отправленная фотография в чате комнаты.
 *
 * <p>Отдельная запись, а не общая с текстовой: у операций разные тела запроса
 * и разные отказы, и одна схема ответа на обе означала бы, что различить их в
 * спецификации нельзя.
 */
@Schema(description = "Отправленная фотография в чате")
public record RoomChatImageResponseDTO(

        @Schema(description = "Созданное сообщение с фотографией")
        RoomChatMessageView message) {
}
