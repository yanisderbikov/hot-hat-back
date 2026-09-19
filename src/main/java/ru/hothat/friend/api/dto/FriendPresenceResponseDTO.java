package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Присутствие всех друзей одним ответом.
 *
 * <p>{@code serverNowMs} здесь не украшение: сегодня «в сети» считает браузер
 * по своим часам ({@code friends/friends.js:11}), и у игрока с уехавшими
 * часами весь список друзей выглядит офлайном. Порог применён на сервере,
 * а метка времени отдана, чтобы клиент мог стареть значение между опросами.
 */
@Schema(description = "Присутствие друзей игрока")
public record FriendPresenceResponseDTO(

        @Schema(description = "Присутствие по каждому другу")
        List<FriendPresenceView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — присутствие едет целиком",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этому ответу", example = "100", type = "integer")
        int limit,

        @Schema(description = "Время сервера, миллисекунды эпохи: от него отсчитан признак online",
                example = "1788600000000", type = "integer")
        long serverNowMs,

        @Schema(description = "Окно, внутри которого игрок считается в сети, миллисекунды", example = "90000", type = "integer")
        long onlineWindowMs) {
}
