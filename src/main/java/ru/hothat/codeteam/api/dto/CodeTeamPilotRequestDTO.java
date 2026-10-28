package ru.hothat.codeteam.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Ручной пилот: вводятся только согласованные тестовые события двух игроков. */
public record CodeTeamPilotRequestDTO(
        @NotBlank @Pattern(regexp = "pair|friends|corporate") String teamClass,
        @NotNull @Size(min = 2, max = 2) List<@Valid Player> players,
        @NotNull @Size(min = 2, max = 30) List<@Valid Evidence> evidence,
        boolean live) {

    public record Player(
            @NotBlank @Size(max = 48) String id,
            @NotBlank @Size(max = 80) String nickname,
            boolean consented) {
    }

    public record Evidence(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,24}") String id,
            @NotBlank @Size(max = 48) String playerId,
            @NotBlank @Pattern(regexp = "speech|guess|hint|turn|score") String type,
            @NotBlank @Size(max = 200) String text,
            String source,
            boolean synthetic) {
    }
}
