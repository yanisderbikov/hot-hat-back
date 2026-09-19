package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Кадр обновления канала {@code /ws/v2/chat/{peerUid}}.
 *
 * <p>Уходит после того, как транзакция отправителя зафиксирована, — и только
 * тогда. Собеседник не должен увидеть сообщение, которого после отката
 * не осталось бы.
 */
@Schema(description = "Кадр изменения переписки")
public record DirectChatEventDTO(

        @Schema(description = "Имя кадра", example = "messages", allowableValues = "messages")
        String type,

        @Schema(description = "Переписка после изменения")
        DirectChatWindowView chat) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public DirectChatEventDTO(DirectChatWindowView chat) {
        this("messages", chat);
    }
}
