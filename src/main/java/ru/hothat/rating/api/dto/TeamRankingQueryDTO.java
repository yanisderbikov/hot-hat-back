package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Какую таблицу команд открыть.
 *
 * <p>Все четыре параметра отбора необязательны, и умолчание у каждого одно —
 * оно живёт в {@code SeasonSelection}. Обёрточные типы, а не примитивы:
 * «не задан год» и «задан нулевой год» — разные случаи, и подставлять текущий
 * сезон вместо нуля молча нельзя.
 *
 * <p>Сезон, режим и дивизион объявлены строками с шаблоном, а не перечислением,
 * намеренно: параметры строки запроса Spring переводит в {@code enum} по имени
 * константы буквально, а на проводе значения строчные ({@code ?division=ru}),
 * и законный запрос отвечал бы 400. Набор от этого не перестаёт быть закрытым —
 * шаблон отвергает чужое значение до входа в контроллер.
 *
 * <p>Курсора в запросе нет: движок за этим адресом отдаёт первую и
 * единственную сотню строк, {@code nextCursor} в ответе всегда пуст, и
 * принимать значение, которое некому выдать, — обман.
 */
@Schema(description = "Параметры таблицы команд")
public record TeamRankingQueryDTO(

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
