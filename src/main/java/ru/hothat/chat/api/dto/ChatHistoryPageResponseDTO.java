package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница истории одной переписки.
 *
 * <p>Заменяет {@code POST /api/portal} с {@code action=get_chat} и живую
 * подписку браузера на {@code directChats/{pair}/messages}
 * ({@code portal.js:121}, {@code friends.js:20}, {@code realtime-social.js:99}).
 */
@Schema(description = "Последние сообщения переписки")
public record ChatHistoryPageResponseDTO(

        @Schema(description = "Собеседник", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
        String peerUid,

        @Schema(description = "Имя собеседника; «Игрок», если карточка не найдена", example = "Petya")
        String peerNickname,

        @Schema(description = "Сообщения от старых к новым — в порядке показа")
        List<ChatMessageView> items,

        @Schema(description = "Курсор следующей страницы. Всегда null: переходный движок умеет отдать "
                + "только последние сообщения, вглубь истории он не ходит",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Сколько сообщений запрошено", example = "30", type = "integer")
        int limit) {
}
