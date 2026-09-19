package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Партия остановлена хозяином комнаты. */
@Schema(description = "Партия на паузе")
public record MatchPausedResponseDTO(

        @Schema(description = "Состояние паузы после нажатия")
        PauseView pause,

        @Schema(description = "Сколько миллисекунд хода заморожено до снятия паузы", example = "23400")
        long frozenTurnRemainingMs,

        @Schema(description = "Сколько миллисекунд голосования заморожено", example = "0")
        long frozenAppealRemainingMs) {
}
