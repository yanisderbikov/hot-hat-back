package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ru.hothat.media.domain.MemeAssetLimits;

/**
 * Заявка на билет для загрузки заставки.
 *
 * <p>Вторая половина прежнего {@code upload_ticket}. Отдельный адрес, а не
 * поле {@code kind}: у заставки другой список типов (картинки, не видео),
 * предел в восемь раз меньше и своя ошибка — {@code MEME_POSTER_TOO_LARGE}
 * вместо {@code MEME_TOO_LARGE} ({@code MediaServiceImpl:115-123}). И, в
 * отличие от ролика, заставка необязательна: её отсутствие ничего не ломает.
 */
@Schema(description = "Запрос билета на загрузку заставки мема")
public record RequestPosterUploadTicketRequestDTO(

        @Schema(description = "Идентификатор мема, к которому относится заставка",
                example = "meme-9f31ab77c204", pattern = "^meme-[A-Za-z0-9_-]{6,100}$")
        @NotBlank(message = "Нужен идентификатор мема.")
        @Pattern(regexp = "^meme-[A-Za-z0-9_-]{6,100}$", message = "Некорректный идентификатор мема.")
        String memeId,

        @Schema(description = "Тип содержимого заставки без параметров: с ним же будет подписана ссылка",
                example = "image/webp", allowableValues = {"image/webp", "image/jpeg", "image/png"})
        @NotBlank(message = "Нужен тип содержимого заставки.")
        @Pattern(regexp = "^image/(?:webp|jpeg|png)$",
                message = "Заставка принимается в форматах webp, jpeg или png.")
        String contentType,

        @Schema(description = "Размер файла в байтах", example = "48213", type = "integer",
                minimum = "1", maximum = "1048576")
        @NotNull(message = "Нужен размер файла.")
        @Min(value = 1, message = "Пустой файл загружать нечем.")
        @Max(value = MemeAssetLimits.MAX_POSTER_BYTES, message = "Заставка больше 1 МБ не принимается.")
        Long sizeBytes,

        @Schema(description = "Дивизион мема: он попадает в путь объекта; можно не задавать — тогда ru",
                example = "ru", allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"},
                nullable = true)
        @Pattern(regexp = "^(?:ru|en|de|es|fr|it|zh|ja|kk)$", message = "Неизвестный дивизион.")
        String divisionLanguage) {
}
