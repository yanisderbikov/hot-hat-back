package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;

import java.util.List;

/**
 * Карточка своей команды.
 *
 * <p>Общая проекция: включается полем в ответ «моя команда» и в ответ лобби
 * команды, а не копируется в оба. Именно из-за этой пары старый движок держал
 * приватный сборщик {@code teamRow(...)}, и его результат уезжал наружу
 * картой без схемы.
 *
 * <p>Показателей сезона здесь нет намеренно: карточка описывает состав, а
 * очки и рейтинг живут в {@link TeamStatsView} — их считает другая таблица и
 * они меняются после каждой партии, тогда как состав не меняется вовсе.
 */
@Schema(description = "Карточка рейтинговой команды")
public record TeamCardView(

        @Schema(description = "Идентификатор команды", example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37")
        String id,

        @Schema(description = "Название команды", example = "Hat Wolves")
        String name,

        @Schema(description = "Кто основал команду: он же владелец приглашения",
                example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String ownerUid,

        @Schema(description = "Оба участника в порядке основания: сначала владелец, затем напарник")
        List<TeamMemberView> members,

        @Schema(description = "Дивизион команды: он же язык слов в партии и ключ рейтинговой таблицы")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Логотип как data-URL; null — логотипа нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String logoDataUrl,

        @Schema(description = "Подтверждена ли команда напарником")
        TeamStatus status,

        @Schema(description = "Когда команду основали, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long createdAtMs) {
}
