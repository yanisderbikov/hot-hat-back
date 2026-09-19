package ru.hothat.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * За какой отрезок посчитана метрика расхода.
 *
 * <p>Набор закрыт: сегодня это свободная строка, и клиент переводит её на
 * русский таблицей из четырёх значений ({@code admin.js:151}), молча показывая
 * сырой ключ для всего остального.
 */
@Schema(description = "Отрезок, за который посчитана метрика")
public enum MetricPeriod {

    /** Сутки: дневная квота. */
    DAY("day"),
    /** Календарный месяц. */
    MONTH("month"),
    /** За всё время: занятое место. */
    TOTAL("total"),
    /** Мгновенное значение: занятая память. */
    CURRENT("current");

    private final String wireValue;

    MetricPeriod(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Значение из хранимого снимка; незнакомое считаем мгновенным. */
    public static MetricPeriod fromWire(String value) {
        for (MetricPeriod period : values()) {
            if (period.wireValue.equals(value)) {
                return period;
            }
        }
        return CURRENT;
    }
}
