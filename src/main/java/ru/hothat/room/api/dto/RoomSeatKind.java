package ru.hothat.room.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Кем человек сидит в комнате.
 *
 * <p>Сегодня это свободная строка в колонке {@code role}, которую пишет
 * браузер: у зрителя туда попадает {@code "spectator"}, у игрока — что
 * угодно, чаще всего ничего. Здесь набор закрыт, и «роль» перестаёт быть
 * полем, которому можно не поверить: место игрока и место зрителя — разные
 * строки в разных таблицах, а это перечисление лишь называет, из какой
 * пришёл автор сообщения.
 */
@Schema(description = "Место в комнате: игрок или зритель")
public enum RoomSeatKind {

    PLAYER("player"),
    SPECTATOR("spectator");

    private final String wireValue;

    RoomSeatKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Значение из старой колонки: всё, что не «зритель», — игрок. */
    public static RoomSeatKind fromWire(String value) {
        return SPECTATOR.wireValue.equals(value) ? SPECTATOR : PLAYER;
    }
}
