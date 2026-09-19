package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.hothat.conference.domain.ConferenceRules;

/** Текстовое сообщение в чат видео-чата. */
@Schema(description = "Текст в чат видео-чата")
public record PostConferenceMessageRequestDTO(

        @Schema(description = "Текст сообщения", example = "Погнали!", maxLength = ConferenceRules.MAX_TEXT_LENGTH)
        @NotBlank(message = "Пустое сообщение отправить нельзя.")
        @Size(max = ConferenceRules.MAX_TEXT_LENGTH, message = "Сообщение не длиннее 1000 символов.")
        String text) {
}
