package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Фотография, прикреплённая к сообщению.
 *
 * <p>Общая проекция: включается полем в ответы разных операций, а не
 * копируется в каждую. Картинка лежит прямо в сообщении data-URL'ом —
 * так её кладёт {@code SocialServiceImpl.cleanAttachment}, отдельного
 * хранилища у чатовых фото нет.
 */
@Schema(description = "Вложенная фотография")
public record ChatImageAttachmentView(

        @Schema(description = "Само изображение data-URL'ом, webp/jpeg/png в base64",
                example = "data:image/webp;base64,UklGRlYAAABXRUJQVlA4…")
        String dataUrl,

        @Schema(description = "Ширина в пикселях, как её посчитал отправитель", example = "840", type = "integer")
        int width,

        @Schema(description = "Высота в пикселях, как её посчитал отправитель", example = "630", type = "integer")
        int height,

        @Schema(description = "Имя исходного файла; показывать не обязательно",
                example = "photo.webp", nullable = true)
        String name) {
}
