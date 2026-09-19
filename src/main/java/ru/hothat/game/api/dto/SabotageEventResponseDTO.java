package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Диверсия применена.
 *
 * <p>Снаряжение приезжает тем же ответом: панель арсенала показывает все
 * счётчики сразу, и после выстрела ей иначе пришлось бы перечитывать своё
 * состояние отдельным запросом.
 */
@Schema(description = "Применённая диверсия")
public record SabotageEventResponseDTO(

        @Schema(description = "Событие для сцены")
        SabotageEventView event,

        @Schema(description = "Снаряжение после выстрела")
        ArsenalView arsenal) {
}
