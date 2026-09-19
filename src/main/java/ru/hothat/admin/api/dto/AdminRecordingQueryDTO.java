package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Отбор страницы админского каталога записей.
 *
 * <p>Незаданный дивизион значит «все»: отдельного магического слова
 * {@code all}, которым сегодня пользуется админка, для этого не нужно —
 * отсутствие отбора выражается отсутствием параметра.
 *
 * <p>Дивизион остаётся строкой, а не перечислением, и это осознанно.
 * Spring переводит строку запроса в константу перечисления по её <b>имени</b>,
 * то есть принял бы только {@code RU}, тогда как во всём остальном API
 * дивизион пишется строчными. Набор от этого не перестаёт быть закрытым:
 * его держит шаблон, и он же виден в спецификации. В телах запросов, где
 * значение разбирает Jackson, дивизион по-прежнему перечисление.
 *
 * <p>Курсора нет: отбор по дивизиону применяется после того, как предел взят
 * у хранилища, поэтому страница бывает неполной, и обещать продолжение было бы
 * враньём. Так же устроена и библиотека мемов.
 */
@Schema(description = "Параметры страницы каталога записей")
public record AdminRecordingQueryDTO(

        @Parameter(description = "Показать только записи этого дивизиона; не задан — все",
                example = "ru")
        @Pattern(regexp = "^(ru|en|de|es|fr|it|zh|ja|kk)$", message = "Неизвестный дивизион.")
        String division,

        @Parameter(description = "Сколько записей просмотреть; по умолчанию 50", example = "50")
        @Min(value = 1, message = "Размер страницы должен быть положительным.")
        @Max(value = 250, message = "Больше двухсот пятидесяти записей за раз не отдаём.")
        Integer limit) {

    /** Тот же предел, что у личной фонотеки в §10.1 плана. */
    public static final int DEFAULT_LIMIT = 50;

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
