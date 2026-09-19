package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Партия началась.
 *
 * <p>Кроме состояния здесь едут числа для аналитики и для тоста на экране:
 * сколько слов собралось и сколько человек вышло играть. Считать их клиенту
 * заново — значит снова читать всю комнату.
 */
@Schema(description = "Начатая партия")
public record StartedMatchResponseDTO(

        @Schema(description = "Номер партии в комнате", example = "3")
        int gameNumber,

        @Schema(description = "Название комнаты", example = "Пятничная")
        String roomName,

        @Schema(description = "Сколько команд играет", example = "3")
        int teamCount,

        @Schema(description = "Сколько игроков вышло на партию", example = "6")
        int playerCount,

        @Schema(description = "Сколько слов собралось в шляпе", example = "60")
        int wordCount,

        @Schema(description = "Сколько человек осталось зрителями", example = "1")
        int spectatorCount,

        @Schema(description = "Состояние партии сразу после старта")
        MatchStateView match) {
}
