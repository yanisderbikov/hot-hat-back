package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог остановки записи партии.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=finish}
 * ({@code app-core.js:8788}, {@code :13000}, {@code :13025}).
 *
 * <p>Готового файла в ответе нет и быть не может: LiveKit останавливает съёмку
 * сразу, а собирает и выкладывает MP4 позже и присылает об этом вебхук.
 * Поэтому здесь только стадия — {@code PROCESSING} у нормально закрытой
 * записи, {@code COMPLETE} у той, что успела выложиться, {@code FAILED} у
 * сорвавшейся. За ссылкой на файл ходят отдельным адресом, когда стадия
 * дойдёт до {@code COMPLETE}.
 */
@Schema(description = "Итог остановки записи партии")
public record FinishedRecordingResponseDTO(

        @Schema(description = "Что произошло", example = "FINISHED")
        RecordingFinishOutcome outcome,

        @Schema(description = "Номер партии, о которой шла речь", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Запись партии; null — записи этой партии нет вовсе",
                example = "hat-0f3a9c1d7b2e5480-3", nullable = true)
        String recordingId,

        @Schema(description = "Стадия записи после остановки; null — останавливать было нечего",
                nullable = true)
        RecordingStatus status) {
}
