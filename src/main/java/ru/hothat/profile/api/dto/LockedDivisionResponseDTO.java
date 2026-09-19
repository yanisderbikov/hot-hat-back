package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Закреплённый дивизион. */
@Schema(description = "Дивизион, закреплённый за игроком")
public record LockedDivisionResponseDTO(

        @Schema(description = "Дивизион игрока; сменить его больше нельзя")
        DivisionLanguage divisionLanguage) {
}
