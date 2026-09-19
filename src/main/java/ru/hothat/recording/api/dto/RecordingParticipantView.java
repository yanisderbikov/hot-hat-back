package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Игрок записанной партии — снимок на момент съёмки.
 *
 * <p>Общая проекция: входит полем в карточку записи, а та — в три ответа.
 *
 * <p>Ник именно снимок, а не ссылка на профиль: игрок мог сменить его после
 * партии, а на видео звучит и написан старый. Показывать под записью
 * сегодняшний ник значило бы спорить с самим видео.
 */
@Schema(description = "Участник записанной партии")
public record RecordingParticipantView(

        @Schema(description = "Игрок", example = "8f3a2b1cQ7dE4rT6yU8iO0pA1sD2")
        String uid,

        @Schema(description = "Как звали игрока в момент партии", example = "Vasya")
        String nickname,

        @Schema(description = "Команда, за которую он играл; null — игрок остался без команды",
                example = "team-1", nullable = true)
        String teamId,

        @Schema(description = "Тестовый бот, а не человек: такие не попадают в число участников записи "
                + "и не получают права на неё", example = "false", type = "boolean")
        boolean testBot) {
}
