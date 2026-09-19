package ru.hothat.lobby.usecase;

import ru.hothat.model.room.Room;
import ru.hothat.team.api.dto.GameMode;
import ru.hothat.util.Divisions;

/**
 * Чтение полей комнаты так, как их показывает лобби.
 *
 * <p>Существует ради одного: витрина и превью обязаны называть режим, язык и
 * имя комнаты одинаково. Пока эти четыре строчки жили копиями в двух
 * сценариях, комната могла попасть в список как «классическая», а в превью —
 * как «с диверсиями», просто потому что одна копия читала колонку, а вторая
 * запасное значение.
 *
 * <p>Язык партии собирается лесенкой {@code gameLanguage → matchmakingLanguage
 * → divisionLanguage}: ровно так его читает выдача видеотокена
 * ({@code TokenServiceImpl.firstNonBlank}), и расхождение означало бы, что в
 * витрине комната одного языка, а внутрь пускают по другому.
 */
final class LobbyRooms {

    private LobbyRooms() {
    }

    /** Название; пустое отдаём как null — «комнату не назвали». */
    static String name(Room room) {
        String name = room.getName();
        return name == null || name.isBlank() ? null : name;
    }

    static GameMode mode(Room room) {
        return GameMode.fromWire(room.getGameMode());
    }

    static String gameLanguage(Room room) {
        return Divisions.normalize(firstNonBlank(
                room.getGameLanguage(), room.getMatchmakingLanguage(), room.getDivisionLanguage()));
    }

    static String divisionLanguage(Room room) {
        return Divisions.normalize(room.getDivisionLanguage());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
