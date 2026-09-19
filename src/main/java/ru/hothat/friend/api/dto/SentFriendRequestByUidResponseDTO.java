package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Заявка, ушедшая по идентификатору игрока. */
@Schema(description = "Созданная заявка в друзья по идентификатору игрока")
public record SentFriendRequestByUidResponseDTO(

        @Schema(description = "Кому ушла заявка")
        InvitedPlayerView addressee) {
}
