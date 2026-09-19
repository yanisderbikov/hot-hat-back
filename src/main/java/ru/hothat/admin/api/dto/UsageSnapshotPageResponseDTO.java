package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница истории снимков расхода.
 *
 * <p>Заменяет {@code GET /api/monitor} — маршрут, который лежал в
 * {@code permitAll} и проверял администратора строкой внутри метода (A12).
 *
 * <p>{@code latest} — всегда самый свежий снимок, даже когда страница показывает
 * старый диапазон. Это не дубль первого элемента: сводка «здоровье систем»
 * обязана оставаться текущей, пока человек листает историю расхода, и клиент
 * сегодня делает ровно это ({@code admin.js:203}).
 */
@Schema(description = "История снимков расхода")
public record UsageSnapshotPageResponseDTO(

        @Schema(description = "Дни истории, свежие сверху")
        List<UsageSnapshotDayView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — движок отдаёт "
                + "первую страницу и не умеет продолжать", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "120",
                type = "integer")
        int limit,

        @Schema(description = "Самый свежий снимок независимо от диапазона; null — снимков ещё нет",
                nullable = true)
        UsageSnapshotView latest,

        @Schema(description = "Последние предупреждения о превышении порогов")
        List<UsageAlertView> alerts) {
}
