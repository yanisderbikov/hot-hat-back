package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница живых комнат вместе со счётчиками текущей нагрузки.
 *
 * <p>Заменяет {@code GET /api/admin?scope=rooms}. Прежний ответ склеивал
 * комнаты со статистикой за период в одном ключе {@code stats}, и клиент
 * доливал в него вторую половину из второго запроса
 * ({@code admin.js:264-269}). Здесь склейки нет: за периодом ходят на
 * {@code …/dashboard/usage}, а сюда — за тем, что происходит сию минуту.
 */
@Schema(description = "Живые комнаты и текущая нагрузка")
public record LiveRoomDashboardResponseDTO(

        @Schema(description = "Открытые комнаты, свежие сверху")
        List<LiveRoomView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — движок отдаёт "
                + "первую страницу и не умеет продолжать", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "200", type = "integer")
        int limit,

        @Schema(description = "Счётчики по этой же выборке плюс общее число учёток")
        LiveActivitySummaryView activity) {
}
