package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.room.api.dto.TurnCredentialsView;

/**
 * Видеотокен тестового бота.
 *
 * <p>Форма та же, что у токена живого игрока, и это не совпадение: в
 * видеокомнате бот занимает обычное место участника с правом публиковать
 * дорожку. Учётка TURN описана общей проекцией из области комнаты —
 * заводить второе описание одних и тех же полей значило бы получить в
 * спецификации две схемы для одного предмета.
 *
 * <p>Личность в ответе повторяет запрошенную. Она нужна не для сведения:
 * по ней владелец сопоставляет пришедшую дорожку с плиткой бота в сетке.
 */
@Schema(description = "Видеотокен тестового бота")
public record TestBotVideoTokenResponseDTO(

        @Schema(description = "Адрес видеоузла", example = "wss://livekit.hot-hat.ru")
        String serverUrl,

        @Schema(description = "Токен участника", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJib3QifQ")
        String participantToken,

        @Schema(description = "Под каким именем бот войдёт в видеокомнату",
                example = "testbot-7b2e5480-1")
        String participantIdentity,

        @Schema(description = "Учётка TURN; null — TURN на сервере не настроен и связь пойдёт напрямую",
                nullable = true)
        TurnCredentialsView turn) {
}
