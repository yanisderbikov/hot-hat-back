package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.friend.api.dto.IncomingFriendRequestView;

import java.util.List;

/**
 * Заявки, ждущие ответа, — та же форма, что у
 * {@code GET /api/v2/friends/requests/incoming}.
 *
 * <p>Заменяет живую подписку на запрос по {@code friendRequests}
 * ({@code realtime-social.js:156}). Заявка здесь — тот же
 * {@link IncomingFriendRequestView}, что отдаёт адрес HTTP: страница заявок
 * рисует список одним кодом, откуда бы он ни приехал.
 */
@Schema(description = "Входящие заявки в друзья")
public record FriendRequestInboxView(

        @Schema(description = "Заявки в порядке, в котором их отдаёт хранилище")
        List<IncomingFriendRequestView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этому списку", example = "50", type = "integer")
        int limit) {
}
