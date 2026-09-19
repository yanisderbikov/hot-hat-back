package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Первый кадр канала {@code /ws/v2/me/social}.
 *
 * <p>Несёт значки шапки портала сразу: число непрочитанных сообщений,
 * ждущие ответа заявки и принятые заявки, которых игрок ещё не видел. Раньше
 * шапка рисовала их пустыми и дорисовывала, когда оживали четыре подписки.
 */
@Schema(description = "Кадр открытия социального канала")
public record SocialChannelHelloDTO(

        @Schema(description = "Имя кадра", example = "hello", allowableValues = "hello")
        String type,

        @Schema(description = "Состояние на момент открытия канала")
        SocialChannelView social) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public SocialChannelHelloDTO(SocialChannelView social) {
        this("hello", social);
    }
}
