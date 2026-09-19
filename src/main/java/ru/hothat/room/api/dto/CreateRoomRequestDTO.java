package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.GameMode;

/**
 * Заявка на новую комнату.
 *
 * <p>Здесь ровно то, что решает создающий. Всего остального в теле нет
 * намеренно: идентификатор комнаты, хозяина, фазу, номер партии, дивизион и
 * счётчик присутствия ставит сервер. До сих пор всё это писал браузер одним
 * пакетом ({@code createRoom()}, {@code app-core.js:12101}), включая
 * {@code createdBy} — то есть «хозяин комнаты» был полем, которое клиент
 * назначал себе сам (находка A1 аудита).
 *
 * <p>Дивизиона в заявке тоже нет: он берётся из карточки игрока. Комната,
 * заведённая в чужом дивизионе, была бы комнатой, в которую её же хозяин не
 * сможет войти.
 */
@Schema(description = "Заявка на создание комнаты")
public record CreateRoomRequestDTO(

        @Schema(description = "Название комнаты; пусто — сервер назовёт её по хвосту идентификатора",
                example = "Комната Васи", maxLength = 80, nullable = true)
        @Size(max = 80, message = "Название комнаты не длиннее 80 символов.")
        String name,

        @Schema(description = "Сколько игроков комната вмещает; по умолчанию 10",
                example = "10", type = "integer", nullable = true)
        @Min(value = 4, message = "В комнате не меньше четырёх мест.")
        @Max(value = 10, message = "В комнате не больше десяти мест.")
        Integer capacity,

        @Schema(description = "Приватная комната не показывается в витрине, не пускает зрителей "
                + "и не требует общего дивизиона; по умолчанию нет",
                example = "false", type = "boolean", nullable = true)
        Boolean privateRoom,

        @Schema(description = "Режим партии; по умолчанию обычная игра", example = "classic",
                nullable = true)
        GameMode gameMode,

        @Schema(description = "Язык слов в партии; по умолчанию — дивизион создающего",
                example = "ru", nullable = true)
        DivisionLanguage gameLanguage,

        @Schema(description = "Тестовая комната: в неё пускают ботов и включают владельческие "
                + "поблажки. Доступна только администратору; остальным поле игнорируется",
                example = "false", type = "boolean", nullable = true)
        Boolean testRoom) {

    /** Единственное место, где живёт умолчание вместимости. */
    public static final int DEFAULT_CAPACITY = 10;

    public int capacityOrDefault() {
        return capacity == null ? DEFAULT_CAPACITY : capacity;
    }

    public boolean privateRoomOrDefault() {
        return Boolean.TRUE.equals(privateRoom);
    }

    public boolean testRoomOrDefault() {
        return Boolean.TRUE.equals(testRoom);
    }

    public GameMode gameModeOrDefault() {
        return gameMode == null ? GameMode.CLASSIC : gameMode;
    }
}
