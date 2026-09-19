package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Подтверждение вебхука.
 *
 * <p>Форма одна на все исходы: LiveKit повторяет уведомление при неуспехе, и
 * различать «проигнорировано» от «не дошло» он должен по телу, а не по коду.
 */
@Schema(description = "Ответ на уведомление LiveKit Egress")
public record EgressWebhookAckResponseDTO(

        @Schema(description = "Что сервер сделал с уведомлением")
        EgressWebhookOutcome outcome,

        @Schema(description = "Имя события, как его прислал LiveKit", example = "egress_ended")
        String event,

        @Schema(description = "Идентификатор записи; null, если уведомление не применялось",
                example = "hat-3f1c9a2b7d4e6501-3", nullable = true)
        String recordingId,

        @Schema(description = "Новое состояние записи; null, если уведомление не применялось",
                example = "complete",
                allowableValues = {"starting", "active", "processing", "complete", "failed", "deleted"},
                nullable = true)
        String status,

        @Schema(description = "Код состояния LiveKit: 0 STARTING … 6 LIMIT_REACHED; "
                + "null, если уведомление не применялось",
                example = "3", type = "integer", nullable = true)
        Integer livekitStatus) {
}
