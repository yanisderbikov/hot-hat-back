package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Публичная карточка чужого игрока.
 *
 * <p>Собирается поле за полем и почты не содержит: прежний документный шлюз
 * отдавал строку профиля целиком, вместе с {@code email},
 * {@code passwordHash} и {@code tokenVersion}.
 */
@Schema(description = "Публичный профиль игрока")
public record PublicPlayerResponseDTO(

        @Schema(description = "Идентификатор игрока", example = "8f3a2b1c9d4e7a6b5c0d1e2f")
        String uid,

        @Schema(description = "Ник игрока", example = "Vasya")
        String nickname,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Дивизион игрока")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Готовая подпись дивизиона с флагом", example = "🇷🇺 Русский дивизион")
        String divisionBadge,

        @Schema(description = "Постоянная рейтинговая команда; null — команды нет", nullable = true)
        RankedTeamView team,

        @Schema(description = "Рейтинги игрока в текущем сезоне")
        PlayerRankingsView rankings) {
}
