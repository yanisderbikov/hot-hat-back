package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Игрок в живой комнате так, как его видит администратор.
 *
 * <p>Это админская проекция, и она шире игроцкой намеренно: почта и признак
 * блокировки нужны, чтобы решение «банить или нет» принималось на том же
 * экране, где видно нарушение. Аватар сюда не входит — карточка комнаты его не
 * рисует, а data-URL аватара весит больше всей остальной строки.
 */
@Schema(description = "Участник живой комнаты глазами администратора")
public record LiveRoomPlayerView(

        @Schema(description = "Идентификатор игрока", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Имя, под которым игрок сидит в комнате", example = "Вася")
        String name,

        @Schema(description = "Команда внутри комнаты; null — игрок ещё не распределён",
                example = "team-a", nullable = true)
        String teamId,

        @Schema(description = "Игрок подавал признаки жизни за последние четыре минуты. "
                + "Считает сервер: у клиента часы могут уехать", example = "true", type = "boolean")
        boolean active,

        @Schema(description = "Это тестовый бот, а не человек: такие всегда считаются активными",
                example = "false", type = "boolean")
        boolean testBot,

        @Schema(description = "Когда игрока видели в последний раз; null — не видели ни разу",
                example = "1788600000000", type = "integer", nullable = true)
        Long lastSeenAtMs,

        @Schema(description = "Почта учётки; null — учётки нет или почта не сохранена",
                example = "player@example.com", nullable = true)
        String email,

        @Schema(description = "Учётка уже заблокирована", example = "false", type = "boolean")
        boolean banned,

        @Schema(description = "Учётка зарегистрирована, а не заведена гостевым входом: "
                + "у неё есть пароль", example = "true", type = "boolean")
        boolean registered) {
}
