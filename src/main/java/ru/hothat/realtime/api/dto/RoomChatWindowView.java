package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.room.api.dto.RoomChatMessageView;

import java.util.List;

/**
 * Хвост чата комнаты — та же форма, что у
 * {@code GET /api/v2/room/{roomId}/chat-messages}.
 *
 * <p>Сообщение здесь — тот же {@link RoomChatMessageView}, что отдаёт адрес
 * HTTP. Прежняя подписка присылала сырые документы ленты вместе со
 * встроенными фотографиями, а прокрутка вверх приезжала другой формой; одна
 * проекция на оба источника делает такое расхождение невыразимым.
 *
 * <p>Окно, а не вся история: канал держит последние сорок сообщений — ровно
 * столько, сколько показывает лента. Вглубь листают по HTTP, курсором.
 */
@Schema(description = "Хвост чата комнаты")
public record RoomChatWindowView(

        @Schema(description = "Сообщения от старых к новым — в порядке показа")
        List<RoomChatMessageView> items,

        @Schema(description = "Курсор следующей страницы вглубь истории; null — история кончилась",
                example = "1788599990000", type = "integer", nullable = true)
        Long nextCursor,

        @Schema(description = "Сколько последних сообщений держит окно", example = "40", type = "integer")
        int limit) {
}
