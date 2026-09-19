package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Последнее сообщение, пришедшее игроку в любую из переписок.
 *
 * <p>Общая проекция входящих: по ней рисуется всплывающее уведомление
 * «сообщение от Пети», не открывая саму переписку.
 */
@Schema(description = "Последнее входящее сообщение")
public record InboxLastMessageView(

        @Schema(description = "Кто написал", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
        String fromUid,

        @Schema(description = "Как показать имя написавшего", example = "Petya", nullable = true)
        String fromNickname,

        @Schema(description = "Начало сообщения, до 200 символов",
                example = "Забирай запись, там на пятой минуте огонь", nullable = true)
        String text,

        @Schema(description = "Когда пришло, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long atMs) {
}
