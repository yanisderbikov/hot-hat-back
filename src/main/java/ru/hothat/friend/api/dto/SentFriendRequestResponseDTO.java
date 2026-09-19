package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Заявка, ушедшая по нику. */
@Schema(description = "Созданная заявка в друзья по нику")
public record SentFriendRequestResponseDTO(

        @Schema(description = "Кому ушла заявка")
        InvitedPlayerView addressee) {
}
