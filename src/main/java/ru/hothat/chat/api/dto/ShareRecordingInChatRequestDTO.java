package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Поделиться записью игры.
 *
 * <p>Отдельный адрес, а не поле {@code attachment.kind} в общей отправке:
 * операция не просто кладёт сообщение, а открывает собеседнику доступ к
 * записи, и падает она по-своему — «записи нет» и «запись не ваша».
 */
@Schema(description = "Запрос на отправку записи игры")
public record ShareRecordingInChatRequestDTO(

        @Schema(description = "Запись, которой делятся. Должна быть сохранена именно вами: "
                + "поделиться чужой записью нельзя",
                example = "hat-0f3a9c1d7b2e5480-3", maxLength = 180)
        @NotBlank(message = "Нужно указать запись.")
        @Size(max = 180, message = "Некорректная запись.")
        @Pattern(regexp = "^[A-Za-z0-9_.:@+-]{3,180}$", message = "Некорректная запись.")
        String recordingId,

        @Schema(description = "Подпись к записи; можно не задавать",
                example = "Смотри с пятой минуты", maxLength = 800, nullable = true)
        @Size(max = 800, message = "Подпись длиннее 800 символов.")
        String caption) {
}
