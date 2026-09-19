package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сообщение чата комнаты.
 *
 * <p>Общая проекция: включается и в страницу истории, и в ответы на отправку
 * текста, на отправку фотографии и на правку, поэтому форма сообщения описана
 * один раз.
 *
 * <p>Условное выражено nullable-полем: у текстового сообщения пусто в
 * {@code image}, у фотографии — пусто в {@code text}. Раньше это был один ключ
 * {@code attachment} произвольной формы, и клиент разбирал его по наличию
 * {@code kind}.
 *
 * <p>Имя автора — снимок на момент отправки, а не текущий ник: человек может
 * сменить ник посреди партии, и переписывать за него уже сказанное неверно.
 */
@Schema(description = "Сообщение чата комнаты")
public record RoomChatMessageView(

        @Schema(description = "Идентификатор сообщения внутри комнаты", example = "chat-9f3a2b1c7d")
        String messageId,

        @Schema(description = "Автор сообщения", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Как звали автора в момент отправки", example = "vasya")
        String name,

        @Schema(description = "Откуда автор писал: со своего места за столом или из зрителей",
                example = "player")
        RoomSeatKind seat,

        @Schema(description = "Текст сообщения; null — сообщение состоит из одной фотографии",
                example = "Скидывайте слова, начинаем", nullable = true)
        String text,

        @Schema(description = "Приложенная фотография; null — сообщение текстовое", nullable = true)
        RoomChatImageView image,

        @Schema(description = "Писал ли тестовый бот: их реплики экран показывает тише",
                example = "false", type = "boolean")
        boolean testBot,

        @Schema(description = "Момент отправки, миллисекунды эпохи; он же курсор страницы",
                example = "1788600000000", type = "integer")
        long createdAtMs) {
}
