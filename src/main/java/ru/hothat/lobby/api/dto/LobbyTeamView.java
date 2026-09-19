package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Команда так, как её видно из лобби.
 *
 * <p>Порядок команд задаёт сервер полем {@code order}, и список приезжает уже
 * отсортированным: раньше сортировку делал браузер
 * ({@code home/home.js:88}), и две страницы могли показать одну комнату
 * по-разному.
 */
@Schema(description = "Команда в составе комнаты")
public record LobbyTeamView(

        @Schema(description = "Идентификатор команды внутри комнаты", example = "team-1")
        String teamId,

        @Schema(description = "Название команды", example = "Команда 1", nullable = true)
        String name,

        @Schema(description = "Место команды в порядке ходов", example = "0", type = "integer")
        int order,

        @Schema(description = "Сколько слов команда уже отгадала", example = "7", type = "integer")
        int score,

        @Schema(description = "Игроки команды в порядке рассадки",
                example = "[\"Qk3xZaTb9mNpR2sVuWyA1cEfGhJk\"]")
        List<String> memberUids) {
}
