package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Строка игрока в таблице сезона.
 *
 * <p>Ник и аватар — снимок на момент последнего зачёта, а не сегодняшний
 * профиль: таблица должна выглядеть одинаково, сколько бы раз игрок ни менял
 * аватар после игры.
 *
 * <p>Команда может быть не заполнена. Личная таблица наполняется зачётом
 * партий, и пока ни одной партии в сезоне не зачли, сервер собирает её из
 * командных очков — в такой строке команды нет.
 */
@Schema(description = "Место игрока в таблице сезона")
public record PlayerStandingView(

        @Schema(description = "Место в таблице, считая с единицы", example = "5", type = "integer")
        int place,

        @Schema(description = "Идентификатор игрока", example = "8f3a2b1c9d4e7a6b5c0d1e2f")
        String uid,

        @Schema(description = "Нынешний ник из карточки игрока: копий ника в таблице сезона нет",
                example = "Vasya", nullable = true)
        String nickname,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Дивизион таблицы", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage,

        @Schema(description = "Команда, за которую игрок набрал эти очки; null — играл вне команды",
                example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37", nullable = true)
        String teamId,

        @Schema(description = "Название этой команды; null — по той же причине",
                example = "Шляпные волки", nullable = true)
        String teamName,

        @Schema(description = "Очки за сезон", example = "620", type = "integer")
        int points,

        @Schema(description = "Сыграно партий", example = "38", type = "integer")
        int games,

        @Schema(description = "Побед", example = "21", type = "integer")
        int wins) {
}
