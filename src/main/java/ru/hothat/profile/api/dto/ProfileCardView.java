package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Карточка игрока так, как её видит он сам.
 *
 * <p>Общая проекция: включается полем в ответ о собственном профиле и в ответ
 * об окончании онбординга — форма описана один раз, различаются только обёртки.
 *
 * <p>Собирается поле за полем из сущности профиля и никогда не сериализует её
 * целиком: в той же строке таблицы лежат {@code passwordHash} и
 * {@code tokenVersion}, которым в браузере делать нечего.
 */
@Schema(description = "Карточка текущего игрока")
public record ProfileCardView(

        @Schema(description = "Ник игрока; сервер выдаёт его при первом входе, пустым не бывает",
                example = "Vasya")
        String nickname,

        @Schema(description = "Аватар как data-URL; null — аватар не загружен",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Дивизион игрока: выбирается один раз и дальше неизменен")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Язык интерфейса: либо язык дивизиона, либо английский")
        DivisionLanguage uiLanguage,

        @Schema(description = "Готовая подпись дивизиона с флагом — её показывают в шапке",
                example = "🇷🇺 Русский")
        String divisionBadge,

        @Schema(description = "Состоит ли игрок в постоянной рейтинговой команде", example = "false", type = "boolean")
        boolean hasRankedTeam) {
}
