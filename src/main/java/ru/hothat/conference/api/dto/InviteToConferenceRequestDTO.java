package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import ru.hothat.common.validation.PlayerUid;

/** Кого позвать: только друга, и только по идентификатору — он уже на экране. */
@Schema(description = "Приглашение друга в видео-чат")
public record InviteToConferenceRequestDTO(

        @Schema(description = "Идентификатор друга", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk",
                pattern = "^[A-Za-z0-9_-]{1,160}$")
        @NotBlank(message = "Укажите игрока.")
        @PlayerUid
        String friendUid) {
}
