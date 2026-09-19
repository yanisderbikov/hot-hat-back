package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Какую таблицу сервер в итоге открыл.
 *
 * <p>Общая проекция: включается полем в ответы всех трёх адресов таблиц, а не
 * копируется в каждый. Нужна потому, что сервер вправе не согласиться с
 * запросом — непонятный сезон, режим или дивизион заменяются умолчанием.
 * Раньше клиент об этом не узнавал и подписывал чужую таблицу заголовком,
 * который сам же и составил из своих параметров.
 */
@Schema(description = "Сезон, режим и дивизион, к которым относится ответ")
public record SeasonSelectionView(

        @Schema(description = "Год сезона", example = "2026", type = "integer")
        int year,

        @Schema(description = "Сезон года", example = "autumn",
                allowableValues = {"winter", "spring", "summer", "autumn"})
        String season,

        @Schema(description = "Режим партии", example = "sabotage",
                allowableValues = {"classic", "sabotage"})
        String mode,

        @Schema(description = "Языковой дивизион", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage) {
}
