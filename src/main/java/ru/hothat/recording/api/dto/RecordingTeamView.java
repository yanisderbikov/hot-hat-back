package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Команда записанной партии со счётом и признаком победы.
 *
 * <p>Общая проекция: входит полем в карточку записи.
 *
 * <p>Победа выражена признаком у команды, а не отдельными массивами
 * {@code winnerTeamIds} и {@code winnerTeamNames} рядом со списком команд, как
 * сегодня. Два параллельных списка приходилось сшивать на клиенте по
 * идентификатору, и при ничьей — а она бывает — сшивка расходилась. В схеме
 * плана это та же колонка {@code recording_team.is_winner}.
 */
@Schema(description = "Команда записанной партии")
public record RecordingTeamView(

        @Schema(description = "Команда внутри партии", example = "team-1")
        String teamId,

        @Schema(description = "Название команды в момент партии", example = "Красные")
        String name,

        @Schema(description = "Состав команды: игроки в том порядке, в каком они в ней были",
                example = "[\"8f3a2b1cQ7dE4rT6yU8iO0pA1sD2\", \"2c9d4e7aP3lM5nB8vC1xZ0qW6eR4\"]")
        List<String> memberUids,

        @Schema(description = "Рейтинговая команда, которой играл этот состав; null — партия не рейтинговая",
                example = "7f1c0b9a3d2e4856", nullable = true)
        String rankedTeamId,

        @Schema(description = "Счёт команды к концу партии", example = "24", type = "integer")
        int score,

        @Schema(description = "Победила ли команда. При ничьей признак стоит у всех, кто набрал "
                + "лучший счёт", example = "true", type = "boolean")
        boolean winner) {
}
