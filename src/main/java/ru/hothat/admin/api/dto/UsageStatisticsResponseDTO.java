package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Статистика за период.
 *
 * <p>Заменяет {@code GET /api/admin?scope=stats}. Из ответа ушёл блок
 * {@code current}: счётчики «прямо сейчас» считаются по живым комнатам и едут
 * вместе с ними ({@code …/dashboard/live-rooms}). Прежде оба блока лежали в
 * одном ключе {@code stats} и клиент сливал их сам из двух ответов.
 */
@Schema(description = "Статистика за выбранный период")
public record UsageStatisticsResponseDTO(

        @Schema(description = "Период, за который посчитан ответ")
        DateRangeView range,

        @Schema(description = "Итоги периода")
        PeriodTotalsView period,

        @Schema(description = "Разбивка по дням в порядке появления событий; дни без событий "
                + "в списке отсутствуют")
        List<DailyActivityView> daily) {
}
