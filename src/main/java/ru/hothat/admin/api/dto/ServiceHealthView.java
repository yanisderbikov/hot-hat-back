package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Одна строка сводки «здоровье систем».
 *
 * <p>Прежняя строка несла и {@code status}, и булев {@code ok}, причём
 * {@code ok} у предупреждения был true, а у неизвестного — null. Второе поле
 * выражало то же самое хуже, и здесь его нет: состояний ровно четыре, они
 * названы.
 */
@Schema(description = "Состояние одной службы")
public record ServiceHealthView(

        @Schema(description = "Название службы", example = "HOT-HAT API")
        String label,

        @Schema(description = "Состояние")
        ServiceHealthStatus status,

        @Schema(description = "Когда проверяли; null — время не сохранилось", example = "1788600000000",
                type = "integer", nullable = true)
        Long checkedAtMs,

        @Schema(description = "Подробность проверки", example = "Доступен")
        String detail,

        @Schema(description = "Сколько миллисекунд отвечала; null — проверка не по сети",
                example = "42", type = "integer", nullable = true)
        Long latencyMs) {
}
