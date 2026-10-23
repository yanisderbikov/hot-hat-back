package ru.hothat.game.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Acknowledgement from the recorded player's browser, never from the attacker. */
public record ConfirmReplacementPlaybackRequestDTO(
        @NotBlank @Pattern(regexp = "^sab_[a-f0-9]{20}$") String eventId,
        boolean played) {
}
