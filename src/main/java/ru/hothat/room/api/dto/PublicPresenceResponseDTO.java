package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Счётчик живых игроков, которым комната светит в витрину.
 *
 * <p>Тела у запроса нет, и это правка контракта относительно плана. Считать
 * число должен сервер: до сих пор его присылал браузер хозяина
 * ({@code publishPublicRoomPresence()}, {@code app-core.js:11931}), а
 * прежний документный шлюз путь комнаты не проверял вовсе — то есть
 * «сколько людей в комнате» было полем, которое клиент назначал сам, и витрина
 * на главной верила ему на слово (находка A1). Присылать серверу число,
 * которое он и так знает, незачем; вызов остался только сигналом «пересчитай».
 *
 * <p>Тест-боты в счёт не идут — кроме тестовой комнаты, где, кроме них, никого
 * и нет.
 */
@Schema(description = "Публичный счётчик игроков комнаты")
public record PublicPresenceResponseDTO(

        @Schema(description = "Комната", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Сколько живых игроков насчитал сервер", example = "6", type = "integer")
        int activePlayers,

        @Schema(description = "Когда посчитали, миллисекунды эпохи", example = "1788600000000",
                type = "integer")
        long measuredAtMs) {
}
