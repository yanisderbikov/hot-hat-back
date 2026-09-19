package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Начатая (или не начатая) сессия съёмки партии.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=start}
 * ({@code app-core.js:8765}, {@code :12974}). Форма одна на все четыре исхода:
 * что именно произошло, говорит {@code outcome}, а то, чего в этом исходе нет,
 * приезжает пустым — клиенту больше не нужно узнавать ответ по набору ключей.
 *
 * <p>Стадии записи здесь намеренно нет, хотя у уже идущей съёмки сервер её
 * знает. Причина простая: у только что созданного задания её ещё нет, и поле
 * оказалось бы заполнено в одном исходе из двух. Спрашивать «а как там
 * рекордер» полагается адресом готовности — тем самым, который клиент и так
 * зовёт следующим шагом.
 */
@Schema(description = "Итог запуска записи партии")
public record RecordingSessionResponseDTO(

        @Schema(description = "Что произошло", example = "STARTED")
        RecordingStartOutcome outcome,

        @Schema(description = "Номер партии, о которой шла речь", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Запись партии; null — снимать нечего (запись выключена "
                + "или комната закрыта)", example = "hat-0f3a9c1d7b2e5480-3", nullable = true)
        String recordingId,

        @Schema(description = "Задание LiveKit Egress, снимающее эту партию; null — задания нет",
                example = "EG_5tK9vQmR2xBd", nullable = true)
        String egressId) {
}
