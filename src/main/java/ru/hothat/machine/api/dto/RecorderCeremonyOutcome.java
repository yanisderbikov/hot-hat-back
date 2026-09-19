package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Чем закончилось завершение съёмки после церемонии награждения.
 *
 * <p>Сегодня три исхода приезжают тремя разными наборами ключей:
 * {@code {finished:true, recordingId}}, {@code {finished:false, recording:{…}}}
 * и {@code {skipped:true, reason:"…"}}. Клиент различает их по наличию ключа —
 * ровно то, что план называет несовместимыми ветками одной операции.
 */
@Schema(description = "Исход завершения съёмки")
public enum RecorderCeremonyOutcome {

    /** Запись остановлена этим вызовом. */
    FINISHED,

    /** Запись уже была завершена раньше: повтор безопасен. */
    ALREADY_FINISHED,

    /** Записи для этой партии не заводилось. */
    NOT_STARTED,

    /** Запись в комнате выключена. */
    RECORDING_DISABLED
}
