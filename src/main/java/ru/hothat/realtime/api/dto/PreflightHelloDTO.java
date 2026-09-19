package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.team.api.dto.PreflightSessionView;

/**
 * Первый кадр канала {@code /ws/v2/team/{teamId}/preflight}.
 *
 * <p>Внутри — тот же {@link PreflightSessionView}, что отдаёт
 * {@code GET /api/v2/team/me/preflight}: три адреса и канал показывают один
 * предмет, описанный один раз.
 *
 * <p>Проверки может не быть вовсе — пара просто открыла свою страницу, — и
 * тогда поле пусто. Это нормальное состояние, а не ошибка: отдельного «не
 * найдено» у канала нет по той же причине, что и у адреса HTTP.
 */
@Schema(description = "Кадр открытия канала проверки готовности")
public record PreflightHelloDTO(

        @Schema(description = "Имя кадра", example = "hello", allowableValues = "hello")
        String type,

        @Schema(description = "Снимок проверки; null — проверка не начата или уже протухла",
                nullable = true)
        PreflightSessionView session) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public PreflightHelloDTO(PreflightSessionView session) {
        this("hello", session);
    }
}
