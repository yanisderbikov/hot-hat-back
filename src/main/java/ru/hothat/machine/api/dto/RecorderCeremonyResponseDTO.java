package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Завершение съёмки после церемонии.
 *
 * <p>Заменяет мутирующий {@code GET /api/recording-state?ceremony_done=1} —
 * единственный из семи режимов, который останавливает Egress и закрывает файл.
 * Это самая явная причина, по которой у машинной поверхности не должно быть
 * мутирующих GET: перезапрос страницы или превентивная загрузка ссылки
 * прокси-сервером обрывали бы запись.
 */
@Schema(description = "Итог завершения съёмки")
public record RecorderCeremonyResponseDTO(

        @Schema(description = "Чем закончилось завершение")
        RecorderCeremonyOutcome outcome,

        @Schema(description = "Идентификатор записи; null, если записи не заводилось",
                example = "hat-3f1c9a2b7d4e6501-3", nullable = true)
        String recordingId,

        @Schema(description = "Состояние записи; null, если записи не заводилось",
                example = "complete",
                allowableValues = {"starting", "active", "processing", "complete", "failed", "deleted"},
                nullable = true)
        String status) {
}
