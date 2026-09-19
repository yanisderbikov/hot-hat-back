package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Строка игрока в рейтинге текущего сезона.
 *
 * <p>Общая проекция: одна и та же строка описывает и классику, и диверсии,
 * различается только режим, под которым она лежит.
 */
@Schema(description = "Место игрока в рейтинге сезона")
public record PlayerRankingView(

        @Schema(description = "Идентификатор игрока", example = "8f3a2b1c9d4e7a6b5c0d1e2f")
        String uid,

        @Schema(description = "Ник на момент последнего пересчёта", example = "Vasya")
        String nickname,

        @Schema(description = "Аватар на момент последнего пересчёта; null — аватара не было",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Дивизион, в котором игрок набирал очки")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Команда, за которую играл; null — играл без команды",
                example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37", nullable = true)
        String teamId,

        @Schema(description = "Название команды; null — играл без команды",
                example = "Шляпные волки", nullable = true)
        String teamName,

        @Schema(description = "Очки за сезон", example = "1240", type = "integer")
        int points,

        @Schema(description = "Сыграно партий", example = "38", type = "integer")
        int games,

        @Schema(description = "Побед", example = "21", type = "integer")
        int wins) {
}
