package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Итог съёмки принят. */
@Schema(description = "Принятый итог съёмки")
public record ReportedClipResultResponseDTO(

        @Schema(description = "Клип сохранён и ждёт применения", example = "true")
        boolean ready,

        @Schema(description = "Клип; пусто, если съёмка не удалась и клип стёрт", nullable = true)
        ReplacementClipView clip) {
}
