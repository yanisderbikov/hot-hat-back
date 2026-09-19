package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ответ на сердцебиение клиента.
 *
 * <p>Обмен {@code ping} → {@code pong} нужен, потому что прокси между
 * браузером и сервером закрывают молчащее соединение, и без
 * этих кадров канал переписки умирал бы в открытом диалоге.
 */
@Schema(description = "Ответ на сердцебиение")
public record ChannelPongFrameDTO(

        @Schema(description = "Имя кадра", example = "pong", allowableValues = "pong")
        String type) {

    public ChannelPongFrameDTO() {
        this("pong");
    }
}
