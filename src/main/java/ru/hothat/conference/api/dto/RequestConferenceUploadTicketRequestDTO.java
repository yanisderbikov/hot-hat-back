package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.hothat.conference.domain.ConferenceRules;

/**
 * Заявка на билет для загрузки файла в чат видео-чата.
 *
 * <p>Тип содержимого любой: в чат кладут скриншоты, видео, документы и
 * архивы. Подпись ссылки считается по точному значению, и заголовок
 * {@code Content-Type} при PUT обязан совпасть с ним буква в букву.
 */
@Schema(description = "Запрос билета на загрузку файла в видео-чат")
public record RequestConferenceUploadTicketRequestDTO(

        @Schema(description = "Имя файла; попадёт в ключ объекта и в подпись вложения", example = "скрин.png",
                maxLength = 160)
        @NotBlank(message = "Нужно имя файла.")
        @Size(max = 160, message = "Имя файла не длиннее 160 символов.")
        String name,

        @Schema(description = "Тип содержимого без параметров: с ним же будет подписана ссылка",
                example = "image/png", pattern = "^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$")
        @NotBlank(message = "Нужен тип содержимого.")
        @Pattern(regexp = "^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$", message = "Некорректный тип содержимого.")
        @Size(max = 120, message = "Слишком длинный тип содержимого.")
        String contentType,

        @Schema(description = "Размер файла в байтах: по нему проверяется предел до загрузки",
                example = "204800", type = "integer", minimum = "1", maximum = "26214400")
        @NotNull(message = "Нужен размер файла.")
        @Min(value = 1, message = "Пустой файл загружать нечем.")
        @Max(value = ConferenceRules.MAX_FILE_BYTES, message = "Файл больше 25 МБ не принимаем.")
        Long sizeBytes) {
}
