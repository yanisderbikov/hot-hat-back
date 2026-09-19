package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Съёмка начата. */
@Schema(description = "Начатая съёмка клипа")
public record ReplacementClipResponseDTO(

        @Schema(description = "Клип, который снимают")
        ReplacementClipView clip,

        @Schema(description = "Событие съёмки для сцены: в состоянии партии и кадре канала его "
                + "получают только снимающий и снимаемый")
        SabotageEventView event,

        @Schema(description = "Сколько миллисекунд идёт съёмка", example = "10000")
        long recordingMs,

        @Schema(description = "Сколько слотов съёмки осталось у заказчика", example = "2")
        int slotsLeft) {
}
