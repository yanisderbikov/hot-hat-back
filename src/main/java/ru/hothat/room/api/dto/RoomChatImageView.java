package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Фотография, приложенная к сообщению чата комнаты.
 *
 * <p>Картинка едет прямо в сообщении data-URL'ом: отдельного хранилища у
 * чатовых фото нет, поэтому объём ограничен, а сжимает её отправитель.
 * Размеры приходят вместе с байтами, чтобы получатель сверстал место под
 * картинку до того, как она загрузится, и лента не прыгала.
 */
@Schema(description = "Фотография в сообщении чата комнаты")
public record RoomChatImageView(

        @Schema(description = "Картинка как data-URL",
                example = "data:image/webp;base64,UklGRh4AAABXRUJQ")
        String dataUrl,

        @Schema(description = "Ширина в точках", example = "840", type = "integer")
        int width,

        @Schema(description = "Высота в точках", example = "472", type = "integer")
        int height,

        @Schema(description = "Имя исходного файла; null — отправитель его не назвал",
                example = "photo.jpg", nullable = true)
        String fileName) {
}
