package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.hothat.conference.domain.ConferenceRules;

/**
 * Сообщение с файлом: файл уже лежит в хранилище по билету на загрузку.
 *
 * <p>Ключ объекта приезжает тем же, каким его выдал билет: сервер строил
 * его сам, и по нему же проверит, что файл лежит в папке этого видео-чата
 * и этого отправителя.
 */
@Schema(description = "Файл в чат видео-чата")
public record PostConferenceFileMessageRequestDTO(

        @Schema(description = "Ключ объекта из билета на загрузку",
                example = "conference/vc-0f3a9c1d7b2e5480/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/1788600000000-9f31ab-скрин.png")
        @NotBlank(message = "Нужен ключ файла.")
        @Size(max = 320, message = "Слишком длинный ключ файла.")
        String storageKey,

        @Schema(description = "Имя файла для подписи вложения", example = "скрин.png", maxLength = 160)
        @NotBlank(message = "Нужно имя файла.")
        @Size(max = 160, message = "Имя файла не длиннее 160 символов.")
        String name,

        @Schema(description = "Тип содержимого без параметров", example = "image/png", maxLength = 120)
        @NotBlank(message = "Нужен тип содержимого.")
        @Size(max = 120, message = "Слишком длинный тип содержимого.")
        String contentType,

        @Schema(description = "Размер файла в байтах", example = "204800", type = "integer",
                minimum = "1", maximum = "26214400")
        @NotNull(message = "Нужен размер файла.")
        @Min(value = 1, message = "Пустой файл отправлять нечем.")
        @Max(value = ConferenceRules.MAX_FILE_BYTES, message = "Файл больше 25 МБ не принимаем.")
        Long sizeBytes,

        @Schema(description = "Подпись к файлу; можно не задавать", example = "вот скрин", nullable = true,
                maxLength = ConferenceRules.MAX_TEXT_LENGTH)
        @Size(max = ConferenceRules.MAX_TEXT_LENGTH, message = "Подпись не длиннее 1000 символов.")
        String text) {
}
