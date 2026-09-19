package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Фотография собеседнику.
 *
 * <p>Отдельный адрес, а не поле {@code attachment.kind} в общей отправке:
 * у картинки свои обязательные поля (сами байты и размер) и своя ошибка —
 * «не влезло». У поделённой записи нет ни того, ни другого.
 */
@Schema(description = "Запрос на отправку фотографии")
public record SendChatImageRequestDTO(

        /**
         * Картинка едет прямо в сообщении: отдельного хранилища у чатовых фото
         * нет. Отсюда и жёсткий предел — 120 000 символов data-URL'а, столько же
         * проверяет браузер, ужимая снимок перед отправкой.
         */
        @Schema(description = "Изображение data-URL'ом: webp, jpeg или png в base64",
                example = "data:image/webp;base64,UklGRlYAAABXRUJQVlA4…",
                pattern = "^data:image/(?:webp|jpeg|png);base64,.+$", maxLength = 120000)
        @NotBlank(message = "Нужно само изображение.")
        @Size(max = 120000, message = "Фото слишком большое.")
        @Pattern(regexp = "(?i)^data:image/(?:webp|jpeg|png);base64,.+$",
                message = "Поддерживаются только webp, jpeg и png.")
        String dataUrl,

        @Schema(description = "Ширина в пикселях: по ней собеседник резервирует место до загрузки",
                example = "840", type = "integer", minimum = "1", maximum = "3000")
        @NotNull(message = "Нужна ширина изображения.")
        @Min(value = 1, message = "Ширина должна быть больше нуля.")
        @Max(value = 3000, message = "Ширина больше 3000 пикселей не поддерживается.")
        Integer width,

        @Schema(description = "Высота в пикселях", example = "630", type = "integer", minimum = "1", maximum = "3000")
        @NotNull(message = "Нужна высота изображения.")
        @Min(value = 1, message = "Высота должна быть больше нуля.")
        @Max(value = 3000, message = "Высота больше 3000 пикселей не поддерживается.")
        Integer height,

        @Schema(description = "Имя исходного файла; можно не задавать — тогда будет «photo»",
                example = "photo.webp", maxLength = 80, nullable = true)
        @Size(max = 80, message = "Имя файла длиннее 80 символов.")
        String name,

        @Schema(description = "Подпись под фотографией; можно не задавать",
                example = "Вот тот самый момент", maxLength = 800, nullable = true)
        @Size(max = 800, message = "Подпись длиннее 800 символов.")
        String caption) {
}
