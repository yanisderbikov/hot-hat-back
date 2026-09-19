package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Строка списка переписок: с кем, о чём было последнее сообщение и сколько
 * непрочитанного.
 *
 * <p>Общая проекция: включается полем в сводку переписок. Ник и аватар
 * собеседника лежат в шапке переписки копией, а не берутся из карточки
 * игрока: список рисуется одним запросом, без похода за профилем каждого.
 */
@Schema(description = "Переписка в списке")
public record ChatThreadView(

        @Schema(description = "Идентификатор переписки: два uid через подчёркивание в алфавитном порядке",
                example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4_8f3a2b1cQ7dE4rT6yU8iO0pA1sD2")
        String id,

        @Schema(description = "Собеседник", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
        String peerUid,

        @Schema(description = "Имя собеседника на момент последнего сообщения", example = "Petya")
        String peerNickname,

        @Schema(description = "Аватар собеседника data-URL'ом; null — аватара нет",
                example = "data:image/webp;base64,UklGRlYAAABXRUJQVlA4…", nullable = true)
        String peerAvatarDataUrl,

        @Schema(description = "Начало последнего сообщения, до 200 символов; у фото и записи это подпись "
                + "вида «📷 Фото». null — в переписке ещё ничего не написано",
                example = "Забирай запись, там на пятой минуте огонь", nullable = true)
        String lastText,

        @Schema(description = "Автор последнего сообщения; null — переписка пуста",
                example = "8f3a2b1cQ7dE4rT6yU8iO0pA1sD2", nullable = true)
        String lastFromUid,

        @Schema(description = "Имя автора последнего сообщения; null — переписка пуста",
                example = "Vasya", nullable = true)
        String lastFromNickname,

        @Schema(description = "Когда переписка последний раз менялась, миллисекунды эпохи; "
                + "по этому полю список отсортирован", example = "1788600000000", type = "integer")
        long updatedAtMs,

        @Schema(description = "Сколько сообщений собеседника вы ещё не прочли", example = "3", type = "integer")
        int unreadCount) {
}
