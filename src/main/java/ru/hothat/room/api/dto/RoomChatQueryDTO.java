package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Какую страницу чата комнаты вернуть.
 *
 * <p>Курсор — момент отправки в миллисекундах, а не смещение: в чате идущей
 * партии сообщения появляются между двумя запросами, и смещение съезжало бы,
 * показывая одно и то же дважды.
 */
@Schema(description = "Параметры чтения чата комнаты")
public record RoomChatQueryDTO(

        @Parameter(description = "Сколько сообщений вернуть; по умолчанию и максимум — 40",
                example = "40")
        @Min(value = 1, message = "Нужно хотя бы одно сообщение.")
        @Max(value = 40, message = "За раз отдаём не больше сорока сообщений.")
        Integer limit,

        @Parameter(description = "Курсор предыдущей страницы: вернуть то, что старше этого момента",
                example = "1788600000000")
        @Min(value = 0, message = "Некорректный курсор.")
        Long before) {

    /** Единственное место, где живёт умолчание: столько же держит лента на экране. */
    public static final int DEFAULT_LIMIT = 40;

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
