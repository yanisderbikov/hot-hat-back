package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Своё снаряжение в партии. */
@Schema(description = "Своё снаряжение")
public record ArsenalResponseDTO(

        @Schema(description = "Боезапас и обойма мемов")
        ArsenalView arsenal,

        @Schema(description = "Свои клипы Подмены: снятые и ждущие применения")
        java.util.List<ReplacementClipView> clips) {
}
