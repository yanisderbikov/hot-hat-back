package ru.hothat.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/** Состояние службы в сводке «здоровье систем». */
@Schema(description = "Состояние службы")
public enum ServiceHealthStatus {

    /** Отвечает и работает. */
    OK("ok"),
    /** Работает, но что-то не настроено. */
    WARN("warn"),
    /** Не отвечает. */
    DOWN("down"),
    /** Данных о ней нет. */
    UNKNOWN("unknown");

    private final String wireValue;

    ServiceHealthStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Значение из хранимого снимка; незнакомое считаем неизвестным. */
    public static ServiceHealthStatus fromWire(String value) {
        for (ServiceHealthStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
