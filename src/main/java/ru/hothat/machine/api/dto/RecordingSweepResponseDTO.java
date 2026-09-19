package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог планового удаления записей с истёкшим сроком.
 *
 * <p>Заменяет {@code GET и POST /api/cleanup-recordings}. Сохранённые кем-то
 * записи не удаляются даже после истечения срока, поэтому просмотрено почти
 * всегда больше, чем удалено, — два счётчика, а не один.
 */
@Schema(description = "Итог уборки просроченных записей")
public record RecordingSweepResponseDTO(

        @Schema(description = "Сколько просроченных записей просмотрено", example = "18", type = "integer")
        int checkedCount,

        @Schema(description = "Сколько записей удалено из хранилища", example = "11", type = "integer")
        int deletedCount,

        @Schema(description = "Верхняя граница прогона: больше записей за один раз не берём",
                example = "100", type = "integer")
        int limit) {
}
