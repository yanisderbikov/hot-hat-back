package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Видеотокен игрока.
 *
 * <p>Токен даёт право публиковать дорожки и подписываться на чужие: это место
 * за столом, а не место в зале. Живёт он два часа — столько же, сколько
 * длинная партия, — и обновляется повторным вызовом.
 *
 * <p>Адрес сервера едет вместе с токеном, а не берётся клиентом из своей
 * сборки: тогда переезд видеоузла не требует пересобирать фронтенд.
 */
@Schema(description = "Видеотокен игрока")
public record PlayerVideoTokenResponseDTO(

        @Schema(description = "Адрес видеоузла", example = "wss://livekit.hot-hat.ru")
        String serverUrl,

        @Schema(description = "Токен участника", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ2YXN5YSJ9")
        String participantToken,

        @Schema(description = "Под каким именем участник войдёт в видеокомнату",
                example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String participantIdentity,

        @Schema(description = "Учётка TURN; null — TURN на сервере не настроен и связь пойдёт напрямую",
                nullable = true)
        TurnCredentialsView turn) {
}
