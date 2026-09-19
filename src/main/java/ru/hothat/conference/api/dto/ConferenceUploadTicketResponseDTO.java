package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Билет на загрузку файла: подписанная ссылка и ключ, который потом называют в сообщении. */
@Schema(description = "Билет на загрузку файла в видео-чат")
public record ConferenceUploadTicketResponseDTO(

        @Schema(description = "Подписанная ссылка для PUT",
                example = "https://s3.hot-hat.ru/hot-hat/conference/vc-0f3a9c1d7b2e5480/...?X-Amz-Signature=...")
        String uploadUrl,

        @Schema(description = "Тип содержимого, которым подписана ссылка: с ним и делать PUT",
                example = "image/png")
        String contentType,

        @Schema(description = "Ключ объекта: его называют в сообщении с файлом",
                example = "conference/vc-0f3a9c1d7b2e5480/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/1788600000000-9f31ab-скрин.png")
        String storageKey,

        @Schema(description = "Имя файла после очистки: с ним файл будет подписан", example = "скрин.png")
        String name,

        @Schema(description = "До какого момента ссылка действительна, миллисекунды эпохи",
                example = "1788600600000", type = "integer")
        long expiresAtMs,

        @Schema(description = "Предел размера файла в байтах", example = "26214400", type = "integer")
        long maxBytes) {
}
