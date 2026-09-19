package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Входящие заявки в друзья. */
@Schema(description = "Заявки, ждущие ответа игрока")
public record IncomingFriendRequestsResponseDTO(

        @Schema(description = "Заявки в порядке, в котором их отдаёт хранилище")
        List<IncomingFriendRequestView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — движок отдаёт список целиком",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этому ответу", example = "50", type = "integer")
        int limit) {
}
