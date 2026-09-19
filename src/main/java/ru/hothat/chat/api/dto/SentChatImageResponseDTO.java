package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Отправленная фотография. */
@Schema(description = "Созданное сообщение с фотографией")
public record SentChatImageResponseDTO(

        @Schema(description = "Сообщение так, как его увидят оба собеседника; поле image заполнено",
                nullable = true)
        ChatMessageView message) {
}
