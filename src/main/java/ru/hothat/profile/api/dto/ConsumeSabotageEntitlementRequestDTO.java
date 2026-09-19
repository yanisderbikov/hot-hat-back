package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import ru.hothat.common.validation.RoomId;

/**
 * Какую партию засчитать в квоту диверсий.
 *
 * <p>Комната и номер партии нужны не для отчётности, а ради повторов: ключ
 * списания сервер складывает сам из них и из идентификатора игрока
 * ({@code ProfileServiceImpl.consumeSabotageGame}). Поэтому второй такой же
 * запрос — переподключение вкладки, повторный старт после ошибки — не
 * тратит вторую партию. Ключ не принимается от клиента: назвав чужой, можно
 * было бы списывать одну и ту же партию бесконечно.
 */
@Schema(description = "Партия, которую засчитываем в квоту диверсий")
public record ConsumeSabotageEntitlementRequestDTO(

        @Schema(description = "Комната, в которой началась партия с диверсиями",
                example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$")
        @NotBlank(message = "Не указана игровая комната.")
        @RoomId
        String roomId,

        @Schema(description = "Номер партии внутри комнаты: вместе с комнатой и игроком образует "
                + "ключ, по которому повтор запроса не тратит вторую партию",
                example = "3", type = "integer", minimum = "0", maximum = "9999")
        @Min(value = 0, message = "Некорректный номер партии.")
        @Max(value = 9999, message = "Некорректный номер партии.")
        int gameNumber) {
}
