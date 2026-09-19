package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Одна строка таблицы «по дням». */
@Schema(description = "Активность за один день периода")
public record DailyActivityView(

        @Schema(description = "День, UTC", example = "2026-09-03", format = "date")
        String day,

        @Schema(description = "Сколько разных игроков в этот день", example = "48", type = "integer")
        int users,

        @Schema(description = "Сколько разных комнат в этот день", example = "12", type = "integer")
        int rooms,

        @Schema(description = "Сколько партий доиграно в этот день", example = "9", type = "integer")
        int games) {
}
