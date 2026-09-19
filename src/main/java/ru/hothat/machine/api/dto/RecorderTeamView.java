package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Команда в кадре: название, счёт и состав. */
@Schema(description = "Команда партии глазами рекордера")
public record RecorderTeamView(

        @Schema(description = "Идентификатор команды внутри комнаты", example = "team-1")
        String id,

        @Schema(description = "Название команды", example = "Красные")
        String name,

        @Schema(description = "Порядковый номер в очереди ходов, начиная с нуля",
                example = "0", type = "integer")
        int order,

        @Schema(description = "Набранные очки", example = "12", type = "integer")
        int score,

        @Schema(description = "Идентификаторы игроков команды")
        List<String> memberUids) {
}
