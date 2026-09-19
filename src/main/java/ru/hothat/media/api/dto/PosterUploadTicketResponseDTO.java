package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Билет на загрузку заставки.
 *
 * <p>Форма повторяет билет на ролик, но это отдельная запись: у неё другие
 * допустимые типы содержимого и другой предел, а общая пара «запрос-ответ»
 * означала бы, что в спецификации у поля {@code contentType} перечислены
 * шесть значений, из которых половина в этом месте недопустима.
 */
@Schema(description = "Билет на загрузку заставки мема")
public record PosterUploadTicketResponseDTO(

        @Schema(description = "Куда класть файл методом PUT",
                example = "https://s3.hot-hat.ru/hot-hat/memes/ru/Qk3xZaTb/meme-9f31ab77c204/poster.webp?X-Amz-Signature=…")
        String uploadUrl,

        @Schema(description = "Заголовок Content-Type запроса PUT: значение должно совпасть с подписью",
                example = "image/webp", allowableValues = {"image/webp", "image/jpeg", "image/png"})
        String contentType,

        @Schema(description = "Ключ объекта в хранилище: его же присылают при публикации мема",
                example = "memes/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/poster.webp")
        String storagePath,

        @Schema(description = "Постоянный адрес файла: он будет работать и после того, как подпись истечёт",
                example = "https://api.hot-hat.ru/api/v2/media/files/memes/ru/Qk3xZaTb/meme-9f31ab77c204/poster.webp")
        String fileUrl,

        @Schema(description = "Когда подпись перестанет действовать, миллисекунды эпохи",
                example = "1788600600000", type = "integer")
        long expiresAtMs,

        @Schema(description = "Предел размера заставки в байтах — тот, по которому выдан билет",
                example = "1048576", type = "integer")
        long maxBytes) {
}
