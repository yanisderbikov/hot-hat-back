package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Текстовое сообщение собеседнику. */
@Schema(description = "Запрос на отправку текстового сообщения")
public record SendChatTextRequestDTO(

        /**
         * Раньше слишком длинный текст молча обрезался до 800 символов уже
         * после отправки, и человек узнавал об этом, увидев обрубок в
         * переписке. Теперь предел объявлен и нарушение видно сразу.
         */
        @Schema(description = "Текст сообщения; пробелы по краям и повторные пробелы сервер схлопывает",
                example = "Забирай запись, там на пятой минуте огонь", maxLength = 800)
        @NotBlank(message = "Сообщение не может быть пустым.")
        @Size(max = 800, message = "Сообщение длиннее 800 символов.")
        String text) {
}
