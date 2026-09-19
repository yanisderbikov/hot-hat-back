package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Расход машины на момент снимка.
 *
 * <p>{@code available} равен false на не-Linux и в снимках, снятых оттуда:
 * в этом случае все метрики null, а причина лежит в {@code unavailableReason}.
 */
@Schema(description = "Расход VPS в снимке")
public record VpsUsageView(

        @Schema(description = "Метрики машины в снимке есть", example = "true", type = "boolean")
        boolean available,

        @Schema(description = "Почему метрик нет; null — они есть",
                example = "Метрики хоста доступны только на Linux (/proc)", nullable = true)
        String unavailableReason,

        @Schema(description = "Диск; null — метрик нет", nullable = true)
        UsageMetricView disk,

        @Schema(description = "Память; null — метрик нет", nullable = true)
        UsageMetricView memory,

        @Schema(description = "Локальные мем-видео; null — метрик нет", nullable = true)
        UsageMetricView mediaStorage,

        @Schema(description = "Сетевой трафик по счётчику ОС; null — метрик нет", nullable = true)
        UsageMetricView networkTotal,

        @Schema(description = "Трафик за календарный месяц, накопленный дельтами; "
                + "null — счётчик ещё не заводили", nullable = true)
        UsageMetricView networkMonthly,

        @Schema(description = "Средняя загрузка за минуту; null — метрик нет", example = "0.42",
                type = "number", nullable = true)
        Double loadAverage,

        @Schema(description = "Сколько ядер у машины; null — метрик нет", example = "4",
                type = "integer", nullable = true)
        Integer cpuCores,

        @Schema(description = "Сокеты к LiveKit; null — посчитать не удалось", example = "18",
                type = "integer", nullable = true)
        Long livekitSockets,

        @Schema(description = "Сокеты к TURN; null — посчитать не удалось", example = "6",
                type = "integer", nullable = true)
        Long turnSockets) {
}
