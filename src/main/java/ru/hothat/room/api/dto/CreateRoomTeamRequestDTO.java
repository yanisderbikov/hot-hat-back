package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Заявка на новую команду в комнате.
 *
 * <p>Ни места в очереди ходов, ни счёта, ни состава в теле нет: очередь
 * назначает сервер по порядку создания, счёт у новой команды нулевой, состав
 * пуст. До сих пор всё это писал браузер ({@code addTeam()},
 * {@code app-core.js:12684}) и он же следил, чтобы {@code teamOrder} в
 * документе комнаты не разошёлся с набором строк команд.
 */
@Schema(description = "Заявка на создание команды")
public record CreateRoomTeamRequestDTO(

        @Schema(description = "Название команды; повторные пробелы сервер схлопывает",
                example = "Соколы", maxLength = 50)
        @NotBlank(message = "У команды должно быть название.")
        @Size(max = 50, message = "Название команды не длиннее 50 символов.")
        String name) {
}
