package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Видео-чат в ответе операций, которые его меняют или показывают. */
@Schema(description = "Видео-чат")
public record ConferenceResponseDTO(

        @Schema(description = "Видео-чат после операции")
        ConferenceView conference) {
}
