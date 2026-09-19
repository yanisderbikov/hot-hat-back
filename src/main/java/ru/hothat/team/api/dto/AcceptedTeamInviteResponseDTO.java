package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;

/**
 * Принятое приглашение.
 *
 * <p>У отказа тела нет, а у согласия есть: согласие подтверждает команду, и
 * клиенту нужно знать, в какой он теперь команде и с кем, — экран показывает
 * «команда подтверждена» до того, как перечитает свою команду.
 */
@Schema(description = "Итог принятия приглашения в команду")
public record AcceptedTeamInviteResponseDTO(

        @Schema(description = "Идентификатор принятого приглашения", example = "4b2c8e1a-9f6d-4057-b3c1-8e2a7d9f0c46")
        String inviteId,

        @Schema(description = "Команда, которая только что стала подтверждённой",
                example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37")
        String teamId,

        @Schema(description = "Название команды", example = "Hat Wolves")
        String teamName,

        @Schema(description = "Напарник: тот, кто звал", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String partnerUid,

        @Schema(description = "Разрешённый ник напарника", example = "vasya")
        String partnerNickname,

        @Schema(description = "Дивизион команды")
        DivisionLanguage divisionLanguage) {
}
