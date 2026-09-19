package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.team.api.dto.PreflightSessionView;

/**
 * Кадр обновления канала {@code /ws/v2/team/{teamId}/preflight}.
 *
 * <p>Заменяет живую подписку на документ {@code rankedTeamPreflights/{teamId}}
 * ({@code portal.js:137}). Ради неё напарник и держал страницу открытой: связь
 * и готовность второго участника не приезжают ни с чем другим, а раньше их
 * добирали перезапросом всей команды — вместе с составом, статистикой сезона и
 * списком приглашений.
 *
 * <p>Пустой снимок здесь значит «проверка кончилась»: её отменили или она
 * протухла. Клиенту это тот же случай, что и отсутствие проверки, поэтому
 * форма одна.
 */
@Schema(description = "Кадр изменения проверки готовности")
public record PreflightEventDTO(

        @Schema(description = "Имя кадра", example = "preflight", allowableValues = "preflight")
        String type,

        @Schema(description = "Снимок проверки после изменения; null — проверка кончилась",
                nullable = true)
        PreflightSessionView session) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public PreflightEventDTO(PreflightSessionView session) {
        this("preflight", session);
    }
}
