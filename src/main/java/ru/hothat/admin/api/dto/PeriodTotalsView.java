package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Итоги периода одной строкой. */
@Schema(description = "Итоги за выбранный период")
public record PeriodTotalsView(

        @Schema(description = "Сколько разных игроков оставили хотя бы одно событие", example = "312",
                type = "integer")
        int uniqueUsers,

        @Schema(description = "Сколько разных комнат упомянуто в событиях", example = "88", type = "integer")
        int uniqueRooms,

        @Schema(description = "Сколько комнат создано", example = "91", type = "integer")
        int roomsCreated,

        @Schema(description = "Сколько раз входили в комнаты", example = "540", type = "integer")
        int roomJoins,

        @Schema(description = "Сколько партий запущено", example = "77", type = "integer")
        int gamesStarted,

        @Schema(description = "Сколько партий доиграно до конца", example = "64", type = "integer")
        int gamesFinished,

        @Schema(description = "Сколько новых учёток заведено. Берётся большее из двух счётчиков — "
                + "событий регистрации и строк в таблице учёток: событие могло не записаться, "
                + "а учётка есть", example = "23", type = "integer")
        long registrations,

        @Schema(description = "Сколько входов в аккаунт", example = "410", type = "integer")
        int logins,

        @Schema(description = "Среднее число игроков в доигранной партии", example = "5.4",
                type = "number")
        double avgPlayersPerFinishedGame,

        @Schema(description = "Среднее число команд в доигранной партии", example = "2.1",
                type = "number")
        double avgTeamsPerFinishedGame,

        @Schema(description = "Среднее число слов в доигранной партии", example = "84.5",
                type = "number")
        double avgWordsPerFinishedGame,

        @Schema(description = "Сколько событий просмотрено при подсчёте", example = "4820",
                type = "integer")
        int eventsScanned,

        @Schema(description = "Предел просмотра достигнут: числа занижены, период надо сузить",
                example = "false", type = "boolean")
        boolean truncated) {
}
