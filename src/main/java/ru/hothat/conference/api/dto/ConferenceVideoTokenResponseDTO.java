package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Видеотокен участника видео-чата.
 *
 * <p>Та же форма, что у видеотокена игрока в комнате: экран созвона
 * подключается тем же транспортом, что и экран игры, и различать ответы
 * ему незачем.
 */
@Schema(description = "Видеотокен участника видео-чата")
public record ConferenceVideoTokenResponseDTO(

        @Schema(description = "Адрес видеоузла", example = "wss://livekit.hot-hat.ru")
        String serverUrl,

        @Schema(description = "Токен участника", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ2YXN5YSJ9")
        String participantToken,

        @Schema(description = "Под каким именем участник войдёт в видеокомнату",
                example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String participantIdentity,

        @Schema(description = "Учётка TURN; null — TURN на сервере не настроен и связь пойдёт напрямую",
                nullable = true)
        ConferenceTurnView turn) {
}
