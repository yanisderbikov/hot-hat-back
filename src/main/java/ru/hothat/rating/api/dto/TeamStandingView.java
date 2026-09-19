package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Строка команды в таблице сезона.
 *
 * <p>Очки, игры и победы — из таблицы сезона; название, логотип и состав —
 * из самой команды. Копий этих полей в строке рейтинга больше нет: они
 * обновлялись только при зачёте следующей партии, и команда, сменившая имя,
 * до конца сезона показывалась под прежним.
 */
@Schema(description = "Место команды в таблице сезона")
public record TeamStandingView(

        @Schema(description = "Место в таблице, считая с единицы. Считает сервер: "
                + "у клиента список может быть отфильтрован, и нумеровать по порядку показа нельзя",
                example = "3", type = "integer")
        int place,

        @Schema(description = "Идентификатор команды", example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37")
        String teamId,

        @Schema(description = "Нынешнее название команды; null — команду распустили, а строка "
                + "сезона её пережила",
                example = "Шляпные волки", nullable = true)
        String name,

        @Schema(description = "Логотип как data-URL; null — логотипа нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String logoDataUrl,

        @Schema(description = "Дивизион таблицы", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage,

        @Schema(description = "Нынешний состав команды: копий состава в таблице сезона нет",
                example = "[\"8f3a2b1c9d4e7a6b5c0d1e2f\", \"2c9d4e7a6b5c0d1e2f8f3a2b\"]")
        List<String> memberUids,

        @Schema(description = "Нынешние ники участников в том же порядке: имя берётся из карточки "
                + "игрока, а не из копии в таблице сезона",
                example = "[\"Vasya\", \"Petya\"]")
        List<String> memberNicknames,

        @Schema(description = "Очки за сезон", example = "1240", type = "integer")
        int points,

        @Schema(description = "Сыграно партий", example = "38", type = "integer")
        int games,

        @Schema(description = "Побед", example = "21", type = "integer")
        int wins,

        @Schema(description = "Сколько раз команде засчитали техническое поражение за обрыв связи",
                example = "1", type = "integer")
        int technicalForfeits,

        @Schema(description = "Есть ли в составе кто-то из ваших друзей: по этому признаку экран "
                + "рейтинга показывает вкладку «друзья». Раньше рядом со списком ехал отдельный "
                + "массив friendTeamIds, и клиент пересекал два списка руками",
                example = "true", type = "boolean")
        boolean hasFriends) {
}
