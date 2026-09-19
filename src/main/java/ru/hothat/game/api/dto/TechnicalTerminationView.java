package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Техническое завершение партии: доигрывать оказалось некому. */
@Schema(description = "Техническое завершение партии")
public record TechnicalTerminationView(

        @Schema(description = "Род завершения", example = "ranked_disconnect",
                allowableValues = {"ranked_disconnect", "casual_disconnect"})
        String type,

        @Schema(description = "Кто не вернулся", example = "[\"pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m\"]")
        List<String> missingUids,

        @Schema(description = "Их имена", example = "[\"Борис\"]")
        List<String> missingNames,

        @Schema(description = "Рейтинг партии аннулирован: наказывать за общий обрыв связи некого",
                example = "false")
        boolean noPenalty,

        @Schema(description = "Когда партия закончилась, миллисекунды эпохи", example = "1757150500000")
        long endedAtMs) {
}
