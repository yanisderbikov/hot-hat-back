package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Кадр обновления канала {@code /ws/v2/me/social}.
 *
 * <p>Уходит после фиксации транзакции: до неё значок непрочитанного показал бы
 * письмо, которого при откате не станет.
 */
@Schema(description = "Кадр изменения социального состояния")
public record SocialChannelEventDTO(

        @Schema(description = "Имя кадра", example = "social", allowableValues = "social")
        String type,

        @Schema(description = "Состояние после изменения")
        SocialChannelView social) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public SocialChannelEventDTO(SocialChannelView social) {
        this("social", social);
    }
}
