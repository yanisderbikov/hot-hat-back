package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Фотография в чат комнаты.
 *
 * <p>Отдельный адрес и отдельная пара DTO, а не ключ внутри общего тела: у
 * текста обязателен текст, у фотографии — байты и размеры, и два набора
 * обязательных полей одной схемой не описываются. Раньше это был один вызов с
 * произвольным {@code attachment}.
 *
 * <p>Сжимает отправитель — до 840 точек по большей стороне и примерно до 120
 * килобайт ({@code compressChatImage()}, {@code app-core.js:8520}); предел
 * здесь чуть шире, чтобы разница в кодировщиках браузеров не отвергала
 * добросовестно сжатую картинку.
 */
@Schema(description = "Фотография в чат комнаты")
public record PostRoomChatImageRequestDTO(

        @Schema(description = "Картинка как data-URL; допустимы только изображения",
                example = "data:image/webp;base64,UklGRh4AAABXRUJQ", maxLength = 160000)
        @NotBlank(message = "Не приложена картинка.")
        @Size(max = 160_000, message = "Картинка слишком большая: сожмите её сильнее.")
        @Pattern(regexp = "^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=]+$",
                message = "Картинка должна быть изображением в data-URL.")
        String dataUrl,

        @Schema(description = "Ширина в точках", example = "840", type = "integer")
        @NotNull(message = "Не указана ширина картинки.")
        @Min(value = 1, message = "Некорректная ширина картинки.")
        @Max(value = 4096, message = "Некорректная ширина картинки.")
        Integer width,

        @Schema(description = "Высота в точках", example = "472", type = "integer")
        @NotNull(message = "Не указана высота картинки.")
        @Min(value = 1, message = "Некорректная высота картинки.")
        @Max(value = 4096, message = "Некорректная высота картинки.")
        Integer height,

        @Schema(description = "Имя исходного файла; показывается при загрузке",
                example = "photo.jpg", maxLength = 80, nullable = true)
        @Size(max = 80, message = "Слишком длинное имя файла.")
        String fileName) {
}
