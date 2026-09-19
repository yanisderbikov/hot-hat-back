package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Отправленное текстовое сообщение. */
@Schema(description = "Созданное текстовое сообщение")
public record SentChatTextResponseDTO(

        @Schema(description = "Сообщение так, как его увидят оба собеседника: "
                + "отправитель рисует его сразу, не дожидаясь живого обновления",
                nullable = true)
        ChatMessageView message) {
}
