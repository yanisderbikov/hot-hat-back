package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Отбор страницы библиотеки мемов.
 *
 * <p>Ни курсора, ни отбора по дивизиону здесь нет, и это решение, а не
 * недосмотр. Хранилище за этим адресом умеет ровно одно — отдать первые
 * {@code limit} строк без снятых с публикации ({@code GetterMedia.getActiveMemes}).
 * Отбор по дивизиону пришлось бы делать уже после того, как предел применён,
 * то есть страница молча оказывалась бы неполной: попросил шестьдесят —
 * получил семь, и не понять, кончилась библиотека или кончилась выборка.
 * Курсор появится вместе с индексом {@code (division, status, created_at DESC)}.
 */
@Schema(description = "Параметры страницы библиотеки мемов")
public record MemeCatalogQueryDTO(

        /**
         * Обёрточный тип: «не задано» и «задано числом» — разные случаи,
         * а единственное место умолчания живёт рядом, в {@link #DEFAULT_LIMIT}.
         */
        @Parameter(description = "Сколько мемов вернуть; по умолчанию 60", example = "60")
        @Min(value = 1, message = "Размер страницы должен быть положительным.")
        @Max(value = 200, message = "Больше двухсот мемов за раз не отдаём.")
        Integer limit) {

    /** Столько карточек помещается на экран библиотеки без дозагрузки. */
    public static final int DEFAULT_LIMIT = 60;

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
