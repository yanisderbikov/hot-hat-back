package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Страница списка друзей. */
@Schema(description = "Страница круга друзей игрока")
public record MyFriendsPageResponseDTO(

        @Schema(description = "Друзья в порядке, в котором их отдаёт хранилище")
        List<FriendCardView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — движок отдаёт список целиком",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "100", type = "integer")
        int limit) {
}
