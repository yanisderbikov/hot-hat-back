package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Созданная команда и обновлённая очередь ходов.
 *
 * <p>Очередь едет целиком, а не одним номером: она и есть то общее, что
 * меняется от появления команды, и вторая поездка за ней означала бы, что
 * между ответом и её чтением экран показывает старый порядок.
 */
@Schema(description = "Созданная команда")
public record RoomTeamResponseDTO(

        @Schema(description = "Новая команда")
        RoomTeamView team,

        @Schema(description = "Очередь ходов после создания: идентификаторы команд по порядку",
                example = "[\"team-1\", \"team-2\"]")
        List<String> teamOrder) {
}
