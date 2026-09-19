package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Какую таблицу игроков открыть.
 *
 * <p>Набор параметров тот же, что у таблицы команд, но пара DTO своя: операции
 * разные, а общий класс запроса связал бы их изменения намертво. Причины
 * строковых типов и отсутствия курсора — те же, что у
 * {@link TeamRankingQueryDTO}.
 */
@Schema(description = "Параметры таблицы игроков")
public record PlayerRankingQueryDTO(

        @Parameter(description = "Сезон года; по умолчанию текущий, он считается по UTC",
                example = "autumn")
        @Pattern(regexp = "^(winter|spring|summer|autumn)$", message = "Такого сезона нет.")
        String season,

        @Parameter(description = "Год сезона; по умолчанию текущий", example = "2026")
        @Min(value = 2026, message = "Рейтинги начались в 2026 году.")
        @Max(value = 2100, message = "Такого сезона ещё нет.")
        Integer year,

        @Parameter(description = "Режим партии; по умолчанию sabotage", example = "sabotage")
        @Pattern(regexp = "^(classic|sabotage)$", message = "Такого режима нет.")
        String mode,

        @Parameter(description = "Языковой дивизион; по умолчанию ru", example = "ru")
        @Pattern(regexp = "^(ru|en|de|es|fr|it|zh|ja|kk)$", message = "Такого дивизиона нет.")
        String division,

        @Parameter(description = "Сколько строк вернуть; по умолчанию и максимум — 100", example = "100")
        @Min(value = 1, message = "Нужна хотя бы одна строка.")
        @Max(value = 100, message = "За раз отдаём не больше ста строк.")
        Integer limit) {

    /** Единственное место умолчания: столько же читает старый движок. */
    public static final int DEFAULT_LIMIT = 100;

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
