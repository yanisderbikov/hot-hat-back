package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Билет на загрузку ролика: подписанная ссылка и всё, чем её открыть.
 *
 * <p>Байты идут мимо бекенда — браузер кладёт файл в хранилище сам. Поэтому
 * ответ обязан назвать не только адрес, но и точный заголовок
 * {@code Content-Type}, и срок: подпись считается по обоим.
 */
@Schema(description = "Билет на загрузку ролика мема")
public record VideoUploadTicketResponseDTO(

        @Schema(description = "Куда класть файл методом PUT",
                example = "https://s3.hot-hat.ru/hot-hat/memes/ru/Qk3xZaTb/meme-9f31ab77c204/video.webm?X-Amz-Signature=…")
        String uploadUrl,

        @Schema(description = "Заголовок Content-Type запроса PUT: значение должно совпасть с подписью",
                example = "video/webm", allowableValues = {"video/webm", "video/mp4", "video/ogg"})
        String contentType,

        @Schema(description = "Ключ объекта в хранилище: его же присылают при публикации мема",
                example = "memes/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/video.webm")
        String storagePath,

        @Schema(description = "Постоянный адрес файла: он будет работать и после того, как подпись истечёт",
                example = "https://api.hot-hat.ru/api/v2/media/files/memes/ru/Qk3xZaTb/meme-9f31ab77c204/video.webm")
        String fileUrl,

        @Schema(description = "Когда подпись перестанет действовать, миллисекунды эпохи",
                example = "1788600600000", type = "integer")
        long expiresAtMs,

        @Schema(description = "Предел размера ролика в байтах — тот, по которому выдан билет",
                example = "8388608", type = "integer")
        long maxBytes) {
}
