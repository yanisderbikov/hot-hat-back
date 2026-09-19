package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Итог доклада о разрыве связи.
 *
 * <p>Единственная ветка, которой позволено закрыть комнату: если из обычной
 * партии исчезли разом все, доигрывать её некому и незачем. У рейтинговой
 * партии этого исхода нет — там результат нужен рейтингу, и комната
 * заканчивается техническим завершением.
 */
@Schema(description = "Итог доклада о разрыве связи")
public record MatchDisconnectionResponseDTO(

        @Schema(description = "Что произошло", example = "PAUSED")
        DisconnectionOutcome outcome,

        @Schema(description = "Состояние паузы")
        PauseView pause,

        @Schema(description = "Кого не хватает на связи", example = "[\"pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m\"]")
        List<String> missingUids,

        @Schema(description = "Техническое завершение, если оно случилось", nullable = true)
        TechnicalTerminationView termination,

        @Schema(description = "Состояние партии; пусто, если комната закрыта", nullable = true)
        MatchStateView match) {
}
