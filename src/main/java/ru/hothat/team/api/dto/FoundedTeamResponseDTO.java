package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;

/**
 * Итог основания команды.
 *
 * <p>Команда создана, но играть ею нельзя: она ждёт ответа напарника. Ответ
 * называет и команду, и приглашение — экран показывает «запрос отправлен» до
 * того, как перечитает свою команду.
 */
@Schema(description = "Основанная команда и отправленное приглашение")
public record FoundedTeamResponseDTO(

        @Schema(description = "Идентификатор созданной команды", example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37")
        String teamId,

        @Schema(description = "Название, под которым команда занята: повторно его занять уже нельзя",
                example = "Hat Wolves")
        String name,

        @Schema(description = "Приглашение, ушедшее напарнику", example = "4b2c8e1a-9f6d-4057-b3c1-8e2a7d9f0c46")
        String inviteId,

        @Schema(description = "Идентификатор приглашённого напарника",
                example = "Zt7bNqXm2wLpK9sVuWyA1cEfGhJk")
        String partnerUid,

        @Schema(description = "Дивизион команды: унаследован у основателя")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Состояние команды. Сразу после основания всегда pending")
        TeamStatus status) {
}
