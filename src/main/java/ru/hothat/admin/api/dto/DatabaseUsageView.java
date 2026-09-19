package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Расход базы данных.
 *
 * <p>Три первые метрики после переезда на PostgreSQL всегда недоступны:
 * пооперационных квот у своей базы нет. Ключи оставлены, потому что по ним
 * рисуется таблица истории, где старые дни ещё несут числа Firestore.
 */
@Schema(description = "Расход базы данных")
public record DatabaseUsageView(

        @Schema(description = "Чтения за сутки")
        UsageMetricView reads,

        @Schema(description = "Записи за сутки")
        UsageMetricView writes,

        @Schema(description = "Удаления за сутки")
        UsageMetricView deletes,

        @Schema(description = "Размер базы")
        UsageMetricView storage) {
}
