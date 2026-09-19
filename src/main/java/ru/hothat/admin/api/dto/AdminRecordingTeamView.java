package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Команда записанной партии со счётом. */
@Schema(description = "Команда записанной партии")
public record AdminRecordingTeamView(

        @Schema(description = "Команда внутри партии", example = "team-a")
        String teamId,

        @Schema(description = "Название команды", example = "Красные")
        String name,

        @Schema(description = "Счёт команды к концу партии", example = "24", type = "integer")
        int score,

        @Schema(description = "Команда победила: её счёт совпал с лучшим. Ничья даёт двух "
                + "победителей — так же считает и сам движок", example = "true", type = "boolean")
        boolean winner) {
}
