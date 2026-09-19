package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Сообщение в чат комнаты.
 *
 * <p>Ни автора, ни его имени, ни роли в теле нет: всё это сервер знает сам —
 * из токена и из места в комнате. До сих пор их присылал браузер
 * ({@code sendChatMessage()}, {@code app-core.js:8610}), то есть подписаться
 * чужим именем в чате комнаты мог кто угодно.
 */
@Schema(description = "Сообщение в чат комнаты")
public record PostRoomChatMessageRequestDTO(

        @Schema(description = "Текст сообщения; повторные пробелы сервер схлопывает",
                example = "Скидывайте слова, начинаем", maxLength = 300)
        @NotBlank(message = "Сообщение не может быть пустым.")
        @Size(max = 300, message = "Сообщение не длиннее 300 символов.")
        String text) {
}
