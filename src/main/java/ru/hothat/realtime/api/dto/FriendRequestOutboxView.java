package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.friend.api.dto.OutgoingFriendRequestView;

import java.util.List;

/**
 * Свои заявки — та же форма, что у {@code GET /api/v2/friends/requests/outgoing}.
 *
 * <p>Заменяет живую подписку на свои заявки ({@code realtime-social.js:158}).
 * Принятые из списка не выброшены: ими живёт значок «друзья» в шапке портала —
 * он считает принятые заявки, которых игрок ещё не видел.
 */
@Schema(description = "Отправленные заявки в друзья")
public record FriendRequestOutboxView(

        @Schema(description = "Заявки: ждущие ответа и принятые")
        List<OutgoingFriendRequestView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этому списку", example = "80", type = "integer")
        int limit) {
}
