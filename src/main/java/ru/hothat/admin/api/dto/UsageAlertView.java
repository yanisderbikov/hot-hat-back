package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Предупреждение о том, что метрика перевалила порог.
 *
 * <p>Одно на метрику и период: повторная тревога по тому же порогу не
 * заводится и письма второй раз не шлёт.
 */
@Schema(description = "Предупреждение о расходе")
public record UsageAlertView(

        @Schema(description = "Идентификатор предупреждения", example = "41")
        String alertId,

        @Schema(description = "По какой метрике сработало", example = "VPS · диск")
        String metricLabel,

        @Schema(description = "Доля израсходованного на момент срабатывания, проценты; "
                + "null — метрика не сохранилась", example = "62.4", type = "number", nullable = true)
        Double metricPercent,

        @Schema(description = "Порог, который перешли, проценты", example = "50", type = "integer")
        int thresholdPercent,

        @Schema(description = "Что стало с письмом", example = "sent",
                allowableValues = {"pending", "sent", "email_failed"})
        String status,

        @Schema(description = "Когда завели предупреждение", example = "1788600000000", type = "integer")
        long createdAtMs) {
}
