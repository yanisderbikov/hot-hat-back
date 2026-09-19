package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Длительность хода.
 *
 * <p>Сегодня кнопка на экране умеет ровно одно значение — шестьдесят секунд, —
 * и клиент пишет его литералом ({@code setTurnDuration()},
 * {@code app-core.js:12790}). Поле здесь всё равно есть: длительность и так
 * лежит в комнате отдельной колонкой, читается правилами хода, и адрес,
 * который умеет записать только одно число, пришлось бы менять от первой же
 * настройки.
 */
@Schema(description = "Длительность хода")
public record SetTurnDurationRequestDTO(

        @Schema(description = "Сколько секунд длится ход", example = "60", type = "integer")
        @NotNull(message = "Не указана длительность хода.")
        @Min(value = 30, message = "Ход не короче 30 секунд.")
        @Max(value = 180, message = "Ход не длиннее 180 секунд.")
        Integer seconds) {
}
