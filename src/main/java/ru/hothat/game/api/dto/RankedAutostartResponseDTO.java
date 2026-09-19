package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог автозапуска рейтинговой партии.
 *
 * <p>Вызов делают все игроки сразу, как только видят полный состав, — и это
 * правильно: ждать хозяина в рейтинге не от кого, комнату собрал подбор.
 * Запускает партию тот, чей запрос пришёл первым, остальные получают
 * {@code ALREADY_RUNNING} и то же состояние.
 */
@Schema(description = "Итог автозапуска рейтинговой партии")
public record RankedAutostartResponseDTO(

        @Schema(description = "Что произошло", example = "WAITING")
        RankedAutostartOutcome outcome,

        @Schema(description = "Сколько игроков уже в комнате", example = "5")
        int playerCount,

        @Schema(description = "Сколько нужно для старта", example = "6")
        int targetPlayers,

        @Schema(description = "Номер начатой партии; ноль, если партия не начата", example = "1")
        int gameNumber,

        @Schema(description = "Сколько слов выдал сервер; ноль, если партия не начата", example = "60")
        int wordCount,

        @Schema(description = "Состояние партии")
        MatchStateView match) {
}
