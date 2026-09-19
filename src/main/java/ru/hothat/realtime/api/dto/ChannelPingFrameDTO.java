package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сердцебиение клиента — единственный кадр, который принимают все каналы.
 *
 * <p>Объявлен записью, хотя сервер его не собирает, а разбирает: без описания
 * входящего кадра спецификация канала неполна — читающий её видит, что сервер
 * отвечает {@code pong}, но не знает, на что.
 *
 * <p>Нужен потому, что прокси между браузером и сервером закрывают молчащее
 * соединение, а игровой экран и диалог держат открытыми часами.
 */
@Schema(description = "Сердцебиение клиента")
public record ChannelPingFrameDTO(

        @Schema(description = "Имя кадра", example = "ping", allowableValues = "ping")
        String type) {
}
