package ru.hothat.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Насколько можно верить числу в метрике.
 *
 * <p>Признак существует потому, что источники разные: размер базы спрашивается
 * у самой базы, а трафик считается дельтами счётчика ОС, который обнуляется
 * при перезагрузке. Показывать оба одинаково значило бы врать про второе.
 */
@Schema(description = "Достоверность значения метрики")
public enum MetricAccuracy {

    /** Спрошено у источника прямо сейчас. */
    EXACT("exact"),
    /** Посчитано приблизительно. */
    ESTIMATE("estimate"),
    /** Не меньше этого: часть расхода не видна. */
    LOWER_BOUND("lower_bound"),
    /** Накоплено нашим счётчиком. */
    TRACKED("tracked"),
    /** Накоплено нашим счётчиком и пересчитано приблизительно. */
    TRACKED_ESTIMATE("tracked_estimate"),
    /** Источник значения не дал. */
    UNAVAILABLE("unavailable");

    private final String wireValue;

    MetricAccuracy(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Значение из хранимого снимка; незнакомое считаем отсутствующим. */
    public static MetricAccuracy fromWire(String value) {
        for (MetricAccuracy accuracy : values()) {
            if (accuracy.wireValue.equals(value)) {
                return accuracy;
            }
        }
        return UNAVAILABLE;
    }
}
