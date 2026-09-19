package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ответ на приглашение.
 *
 * <p>Видео-чат приложен только к принятию: отклонивший не участник и видеть
 * состав не вправе.
 */
@Schema(description = "Отвеченное приглашение в видео-чат")
public record AnsweredConferenceInviteResponseDTO(

        @Schema(description = "Видео-чат, о котором шла речь", example = "vc-0f3a9c1d7b2e5480")
        String conferenceId,

        @Schema(description = "true — вступил, false — отклонил", example = "true", type = "boolean")
        boolean accepted,

        @Schema(description = "Видео-чат после вступления; null у отклонения", nullable = true)
        ConferenceView conference) {
}
