package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ru.hothat.media.domain.MemeAssetLimits;

/**
 * Заявка на билет для загрузки ролика.
 *
 * <p>Половина прежнего {@code upload_ticket}: там вид файла выбирался полем
 * {@code kind}, и одна пара «запрос-ответ» описывала две операции с разными
 * типами содержимого, разными пределами и разными ошибками. У ролика предел
 * восемь мегабайт и три допустимых типа; у заставки — свои, и она живёт по
 * своему адресу.
 */
@Schema(description = "Запрос билета на загрузку ролика мема")
public record RequestVideoUploadTicketRequestDTO(

        @Schema(description = "Идентификатор мема, под который загружается ролик",
                example = "meme-9f31ab77c204", pattern = "^meme-[A-Za-z0-9_-]{6,100}$")
        @NotBlank(message = "Нужен идентификатор мема.")
        @Pattern(regexp = "^meme-[A-Za-z0-9_-]{6,100}$", message = "Некорректный идентификатор мема.")
        String memeId,

        /**
         * Без параметров: подпись ссылки считается по точному значению, и
         * заголовок {@code Content-Type} при PUT обязан совпасть с ним буква
         * в букву. Браузер отдаёт {@code blob.type} вида
         * {@code video/webm;codecs=vp9}, и раньше сервер молча отбрасывал
         * хвост — подпись расходилась с заголовком, а хранилище отвечало 403,
         * которое клиент показывал как «не удалось загрузить медиа».
         */
        @Schema(description = "Тип содержимого ролика без параметров: с ним же будет подписана ссылка",
                example = "video/webm", allowableValues = {"video/webm", "video/mp4", "video/ogg"})
        @NotBlank(message = "Нужен тип содержимого ролика.")
        @Pattern(regexp = "^video/(?:webm|mp4|ogg)$",
                message = "Ролик принимается в форматах webm, mp4 или ogg.")
        String contentType,

        @Schema(description = "Размер файла в байтах: по нему проверяется предел до загрузки",
                example = "3145728", type = "integer", minimum = "1", maximum = "8388608")
        @NotNull(message = "Нужен размер файла.")
        @Min(value = 1, message = "Пустой файл загружать нечем.")
        @Max(value = MemeAssetLimits.MAX_VIDEO_BYTES, message = "Ролик больше 8 МБ не принимаем.")
        Long sizeBytes,

        @Schema(description = "Дивизион мема: он попадает в путь объекта; можно не задавать — тогда ru",
                example = "ru", allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"},
                nullable = true)
        @Pattern(regexp = "^(?:ru|en|de|es|fr|it|zh|ja|kk)$", message = "Неизвестный дивизион.")
        String divisionLanguage) {
}
