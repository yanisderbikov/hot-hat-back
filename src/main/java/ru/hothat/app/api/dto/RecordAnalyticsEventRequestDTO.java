package ru.hothat.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ru.hothat.common.validation.RoomId;

/**
 * Событие, о котором сообщает браузер.
 *
 * <p>Личность в теле не передаётся: uid и почту к событию приписывает сервер
 * из токена. Раньше их тоже ставил сервер, но проверить это по контракту было
 * нельзя — тело объявлено {@code Map<String,Object>}, и клиент мог прислать
 * что угодно, включая чужой {@code uid}: неизвестные ключи молча терялись,
 * а не отвергались.
 */
@Schema(description = "Клиентское событие для отчётов")
public record RecordAnalyticsEventRequestDTO(

        @Schema(description = "Вид события", example = "game_finished")
        @NotNull(message = "Нужен вид события.")
        AnalyticsEventType eventType,

        /**
         * Ключ идемпотентности, а не идентификатор строки: обработчик конца
         * партии срабатывает у каждого участника, и одно событие приезжает
         * на сервер пять раз. Повтор с тем же ключом ничего не меняет.
         *
         * <p>Старый движок вычищал из ключа посторонние знаки, подменяя их
         * подчёркиванием: два разных ключа могли стать одним, и второе
         * событие пропадало без следа. Здесь ключ проверяется, а не чинится.
         */
        @Schema(description = "Ключ, по которому повтор события распознаётся как тот же самый",
                example = "hat-0f3a9c1d7b2e5480-game-2-finished", pattern = "^[A-Za-z0-9._~-]{1,160}$")
        @NotBlank(message = "Нужен ключ события.")
        @Pattern(regexp = "^[A-Za-z0-9._~-]{1,160}$", message = "Ключ события содержит недопустимые знаки.")
        String eventKey,

        @Schema(description = "Комната, в которой произошло событие; у событий учётки её нет",
                example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$", nullable = true)
        @RoomId
        String roomId,

        @Schema(description = "Подробности события; можно не передавать вовсе", nullable = true)
        @Valid
        AnalyticsEventPayloadView payload) {
}
