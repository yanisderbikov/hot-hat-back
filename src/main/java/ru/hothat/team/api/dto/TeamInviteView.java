package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;

/**
 * Приглашение в команду, ждущее ответа игрока.
 *
 * <p>Своего идентификатора приглашённого здесь нет: список отдаётся только
 * своему хозяину, и повторять в каждой строке того, кто её и запросил, незачем.
 */
@Schema(description = "Входящее приглашение в рейтинговую команду")
public record TeamInviteView(

        @Schema(description = "Идентификатор приглашения: им отвечают согласием или отказом",
                example = "4b2c8e1a-9f6d-4057-b3c1-8e2a7d9f0c46")
        String inviteId,

        @Schema(description = "Команда, в которую зовут", example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37")
        String teamId,

        @Schema(description = "Название команды", example = "Hat Wolves")
        String teamName,

        @Schema(description = "Кто зовёт: основатель команды", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String ownerUid,

        @Schema(description = "Ник основателя из его карточки: копии ника в приглашении больше "
                + "нет — между приглашением и ответом ник могли сменить", example = "vasya")
        String ownerNickname,

        @Schema(description = "Аватар основателя как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String ownerAvatarDataUrl,

        @Schema(description = "Дивизион команды. Принять приглашение можно только в своём дивизионе")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Когда позвали, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long createdAtMs) {
}
