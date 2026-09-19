package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Свои приглашения в видео-чаты, ждущие ответа, новые сверху. */
@Schema(description = "Приглашения в видео-чаты")
public record ConferenceInvitesResponseDTO(

        @Schema(description = "Приглашения без ответа; истёкшие не показываются")
        List<ConferenceInviteView> items) {
}
