package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Свои заявки в друзья.
 *
 * <p>Этим ответом живёт значок «друзья» в шапке портала
 * ({@code portal-shell.js:432}): он считает принятые заявки, которых игрок
 * ещё не видел. Поэтому принятые здесь не отфильтрованы — они и есть повод
 * зажечь значок.
 */
@Schema(description = "Заявки, отправленные игроком")
public record OutgoingFriendRequestsResponseDTO(

        @Schema(description = "Заявки: ждущие ответа и принятые")
        List<OutgoingFriendRequestView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — движок отдаёт список целиком",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этому ответу", example = "80", type = "integer")
        int limit) {
}
