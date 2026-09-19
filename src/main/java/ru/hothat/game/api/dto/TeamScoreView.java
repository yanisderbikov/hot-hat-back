package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Команда партии и её счёт. */
@Schema(description = "Команда партии")
public record TeamScoreView(

        @Schema(description = "Идентификатор команды", example = "team_7d2c9a1b")
        String teamId,

        @Schema(description = "Название", example = "Красные")
        String name,

        @Schema(description = "Место в очереди ходов", example = "0")
        int order,

        @Schema(description = "Счёт партии", example = "12")
        int score,

        @Schema(description = "Состав, замороженный на старте партии",
                example = "[\"kZ8Qw1nBv2mX3cL4aS5dF6gH7jK8\",\"pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m\"]")
        List<String> memberUids) {
}
