package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Заряженная обойма. */
@Schema(description = "Итог зарядки обоймы")
public record MatchLoadoutResponseDTO(

        @Schema(description = "Снаряжение после зарядки")
        ArsenalView arsenal) {
}
