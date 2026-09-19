package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Видеотокен зрителя.
 *
 * <p>Отдельная запись, а не общая с игроцкой: этот токен только на приём, и
 * различие должно быть видно в схеме, а не в поле {@code role} внутри общего
 * ответа. Публиковать дорожки такой токен не позволяет.
 */
@Schema(description = "Видеотокен зрителя")
public record SpectatorVideoTokenResponseDTO(

        @Schema(description = "Адрес видеоузла", example = "wss://livekit.hot-hat.ru")
        String serverUrl,

        @Schema(description = "Токен участника, только на приём",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwZXR5YSJ9")
        String participantToken,

        @Schema(description = "Под каким именем зритель войдёт в видеокомнату",
                example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
        String participantIdentity,

        @Schema(description = "Учётка TURN; null — TURN на сервере не настроен", nullable = true)
        TurnCredentialsView turn) {
}
