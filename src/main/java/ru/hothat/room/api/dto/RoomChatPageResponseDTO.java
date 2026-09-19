package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница чата комнаты.
 *
 * <p>Заменяет живую подписку браузера на {@code rooms/{id}/chat}
 * ({@code app-core.js:8795}), которая тянула ленту целиком — вместе со
 * встроенными в сообщения фотографиями, по сто с лишним килобайт каждая.
 *
 * <p>Живые сообщения приходят каналом {@code /ws/v2/room/{roomId}}. Этот
 * адрес — первый экран и прокрутка вверх.
 */
@Schema(description = "Страница чата комнаты")
public record RoomChatPageResponseDTO(

        @Schema(description = "Сообщения от старых к новым — в порядке показа")
        List<RoomChatMessageView> items,

        @Schema(description = "Курсор следующей страницы вглубь истории; null — история кончилась",
                example = "1788599990000", type = "integer", nullable = true)
        Long nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "40",
                type = "integer")
        int limit) {
}
