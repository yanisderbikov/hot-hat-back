package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Открытая комната со своим составом. */
@Schema(description = "Живая комната в дашборде администратора")
public record LiveRoomView(

        @Schema(description = "Комната", example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Название комнаты; если его не задавали — её идентификатор",
                example = "Пятничная шляпа")
        String name,

        @Schema(description = "Фаза, в которой комната находится сейчас", example = "playing")
        String phase,

        @Schema(description = "Когда комнату создали", example = "1788596400000", type = "integer")
        long createdAtMs,

        @Schema(description = "Когда комнату меняли в последний раз: по этому полю "
                + "отсортирована страница", example = "1788600000000", type = "integer")
        long updatedAtMs,

        @Schema(description = "Номер партии внутри комнаты", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Сколько слов лежит в шляпе", example = "87", type = "integer")
        int wordCount,

        @Schema(description = "Сколько команд собрано", example = "2", type = "integer")
        int teamCount,

        @Schema(description = "Сколько участников подавали признаки жизни за последние четыре минуты",
                example = "5", type = "integer")
        int activePlayers,

        @Schema(description = "Состав комнаты целиком, включая неактивных")
        List<LiveRoomPlayerView> players) {
}
