package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Ответ на приглашение друга: исход и видео-чат после него. */
@Schema(description = "Отправленное приглашение в видео-чат")
public record SentConferenceInviteResponseDTO(

        @Schema(description = "Кого звали", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String invitedUid,

        @Schema(description = "Чем кончилось")
        ConferenceInviteOutcome outcome,

        @Schema(description = "Видео-чат после приглашения")
        ConferenceView conference) {
}
