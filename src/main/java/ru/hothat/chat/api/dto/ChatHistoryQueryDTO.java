package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Сколько последних сообщений переписки вернуть.
 *
 * <p>Курсора здесь пока нет намеренно: переходный движок
 * ({@code SocialServiceImpl.getChat}) читает ровно тридцать последних
 * сообщений и попросить у него больше или «то, что было раньше вот этого»
 * нельзя. Объявить параметр, который сервер молча проигнорирует, хуже,
 * чем его не объявлять.
 */
@Schema(description = "Параметры чтения истории переписки")
public record ChatHistoryQueryDTO(

        @Parameter(description = "Сколько последних сообщений вернуть; по умолчанию и максимум — 30",
                example = "30")
        @Min(value = 1, message = "Нужно хотя бы одно сообщение.")
        @Max(value = 30, message = "За раз отдаём не больше тридцати сообщений.")
        Integer limit) {

    /** Единственное место, где живёт умолчание: столько же читает старый движок. */
    public static final int DEFAULT_LIMIT = 30;

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
