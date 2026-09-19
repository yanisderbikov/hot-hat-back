package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Постоянная рейтинговая команда игрока. */
@Schema(description = "Рейтинговая команда")
public record RankedTeamView(

        @Schema(description = "Идентификатор команды", example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37")
        String id,

        @Schema(description = "Название команды", example = "Шляпные волки")
        String name,

        @Schema(description = "Логотип как data-URL; null — логотипа нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String logoDataUrl,

        @Schema(description = "Дивизион команды")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Идентификаторы участников",
                example = "[\"8f3a2b1c9d4e7a6b5c0d1e2f\", \"2c9d4e7a6b5c0d1e2f8f3a2b\"]")
        List<String> memberUids,

        @Schema(description = "Ники участников в том же порядке",
                example = "[\"Vasya\", \"Petya\"]")
        List<String> memberNicknames) {
}
