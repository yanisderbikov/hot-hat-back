package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Отправленное сообщение в той же форме, в какой его увидят остальные. */
@Schema(description = "Отправленное сообщение видео-чата")
public record SentConferenceMessageResponseDTO(

        @Schema(description = "Сообщение, как оно записано")
        ConferenceMessageView message) {
}
