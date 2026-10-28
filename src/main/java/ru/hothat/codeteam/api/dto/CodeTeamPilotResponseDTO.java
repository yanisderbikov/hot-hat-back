package ru.hothat.codeteam.api.dto;

/** Превью бесплатно; live — одна оплачиваемая генерация GenAPI. */
public record CodeTeamPilotResponseDTO(
        String mode,
        String model,
        int evidenceCount,
        String prompt,
        String analysis) {
}
