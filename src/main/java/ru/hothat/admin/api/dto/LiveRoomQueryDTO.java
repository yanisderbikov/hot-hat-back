package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Отбор страницы живых комнат.
 *
 * <p>Курсора нет: хранилище за этим адресом умеет только «отдай первые
 * {@code limit} комнат» ({@code GetterRoom.getAll}), а закрытые отсеиваются уже
 * после предела. Обещать продолжение страницы, которого движок дать не может,
 * — врать в спецификации; курсор появится вместе с отбором по фазе в самом
 * запросе.
 */
@Schema(description = "Параметры страницы живых комнат")
public record LiveRoomQueryDTO(

        /**
         * Обёрточный тип: «не задано» и «задано числом» — разные случаи,
         * а единственное место умолчания живёт рядом, в {@link #DEFAULT_LIMIT}.
         */
        @Parameter(description = "Сколько комнат просмотреть; по умолчанию 200", example = "200")
        @Min(value = 1, message = "Размер страницы должен быть положительным.")
        @Max(value = 500, message = "Больше пятисот комнат за раз не отдаём.")
        Integer limit) {

    /** Тот же предел, с которым дашборд читал комнаты до переезда. */
    public static final int DEFAULT_LIMIT = 200;

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
