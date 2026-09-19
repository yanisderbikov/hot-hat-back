package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отрезок, за который на самом деле посчитан ответ.
 *
 * <p>Возвращается всегда, а не только когда клиент его задал: сервер вправе
 * развернуть перевёрнутый диапазон и обрезать слишком длинный, и умолчать об
 * этом значило бы подписать таблицу чужими датами.
 */
@Schema(description = "Период, за который посчитан ответ")
public record DateRangeView(

        @Schema(description = "Первый день периода, UTC", example = "2026-08-30", format = "date")
        String startDay,

        @Schema(description = "Последний день периода, UTC", example = "2026-09-06", format = "date")
        String endDay,

        @Schema(description = "Начало периода", example = "1788048000000", type = "integer")
        long startAtMs,

        @Schema(description = "Конец периода", example = "1788652799999", type = "integer")
        long endAtMs) {
}
