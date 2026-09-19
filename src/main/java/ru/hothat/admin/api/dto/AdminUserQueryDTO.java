package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Отбор страницы реестра учёток.
 *
 * <p>Предел по умолчанию — сто, а не пять тысяч, как сегодня
 * ({@code ProfileServiceImpl:647}). Пять тысяч профилей с почтой в одном
 * ответе — это не страница, а выгрузка базы, и просить её случайно не должно
 * получаться.
 */
@Schema(description = "Параметры страницы реестра учёток")
public record AdminUserQueryDTO(

        @Parameter(description = "Сколько учёток вернуть; по умолчанию 100", example = "100")
        @Min(value = 1, message = "Размер страницы должен быть положительным.")
        @Max(value = 500, message = "Больше пятисот учёток за раз не отдаём.")
        Integer limit) {

    /** Тот же предел, что у остальных списков §10.1 плана. */
    public static final int DEFAULT_LIMIT = 100;

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
