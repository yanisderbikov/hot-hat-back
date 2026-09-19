package ru.hothat.machine.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Вид диверсии, который рекордер вправе нарисовать.
 *
 * <p>Набор закрыт не для красоты: {@code RecorderStateServiceImpl.safeSabotageEvent}
 * отбрасывает событие незнакомого вида, чтобы в кадр не уехало то, чего
 * страница записи рисовать не умеет. Раньше это был список строк внутри метода,
 * теперь — перечисление в схеме.
 */
@Schema(description = "Вид диверсии")
public enum RecorderSabotageType {

    TOMATO("tomato"),
    MEME("meme"),
    CROCODILE("crocodile"),
    FART("fart");

    private final String wireValue;

    RecorderSabotageType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** @return вид события или {@code null}, если он не из этого набора. */
    public static RecorderSabotageType fromWire(String value) {
        for (RecorderSabotageType type : values()) {
            if (type.wireValue.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
