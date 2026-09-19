package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Отправленная запись игры. */
@Schema(description = "Созданное сообщение с записью игры")
public record SharedRecordingInChatResponseDTO(

        @Schema(description = "Сообщение так, как его увидят оба собеседника; поле recording заполнено",
                nullable = true)
        ChatMessageView message) {
}
