package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Кадр обновления канала {@code /ws/v2/conference/{id}}.
 *
 * <p>Уходит после того, как транзакция писателя зафиксирована, — и только
 * тогда. Участник не должен увидеть сообщение или приглашение, которого
 * после отката не осталось бы.
 */
@Schema(description = "Кадр изменения видео-чата")
public record ConferenceChannelEventDTO(

        @Schema(description = "Имя кадра", example = "conference", allowableValues = "conference")
        String type,

        @Schema(description = "Видео-чат после изменения")
        ConferenceChannelView conference) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public ConferenceChannelEventDTO(ConferenceChannelView conference) {
        this("conference", conference);
    }
}
