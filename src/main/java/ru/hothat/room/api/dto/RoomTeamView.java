package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Команда внутри комнаты.
 *
 * <p>Общая проекция: включается полем в снимок комнаты, в ответ на создание
 * команды, на вход в неё и в итог жеребьёвки.
 *
 * <p>Порядок задаёт сервер полем {@code order}, и список приезжает уже
 * отсортированным. Раньше очередь ходов вычислялась из массива
 * {@code teamOrder} в документе комнаты, а состав — из второго массива в
 * строке команды, и разойтись они могли на любой правке.
 */
@Schema(description = "Команда в составе комнаты")
public record RoomTeamView(

        @Schema(description = "Идентификатор команды внутри комнаты", example = "team-1")
        String teamId,

        @Schema(description = "Название команды", example = "Соколы", nullable = true)
        String name,

        @Schema(description = "Место команды в очереди ходов", example = "0", type = "integer")
        int order,

        @Schema(description = "Сколько слов команда отгадала в текущей партии",
                example = "7", type = "integer")
        int score,

        @Schema(description = "Игроки команды в порядке рассадки",
                example = "[\"Qk3xZaTb9mNpR2sVuWyA1cEfGhJk\"]")
        List<String> memberUids,

        @Schema(description = "Постоянная команда, которой принадлежит это место за столом; "
                + "null — сборная на одну партию", example = "team-3f1c9a2b", nullable = true)
        String rankedTeamId) {
}
