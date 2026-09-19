package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Итог сверки присутствия.
 *
 * <p>Сверку заказывает один участник партии — тот, чей клиент взял на себя
 * роль ведущего опрос. Ответ одинаков для всех исходов: слово в
 * {@code outcome} и подробности в необязательных полях.
 */
@Schema(description = "Итог сверки присутствия")
public record MatchPresenceResponseDTO(

        @Schema(description = "Что произошло", example = "PAUSED")
        MatchPresenceOutcome outcome,

        @Schema(description = "Состояние паузы")
        PauseView pause,

        @Schema(description = "Кого не хватает на связи", example = "[\"pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m\"]")
        List<String> missingUids,

        @Schema(description = "Сколько миллисекунд ждём пропавших до технического завершения",
                example = "90000")
        long disconnectLimitMs,

        @Schema(description = "Техническое завершение, если оно случилось", nullable = true)
        TechnicalTerminationView termination,

        @Schema(description = "Состояние партии после сверки")
        MatchStateView match) {
}
