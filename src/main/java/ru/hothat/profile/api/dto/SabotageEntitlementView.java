package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Квота партий в режиме диверсий: пять бесплатных на учётную запись.
 *
 * <p>Общая проекция, а не поля в ответе. Квоту показывают две операции —
 * чтение перед входом в режим и списание при старте партии, — и обе обязаны
 * называть её одинаково. Пока это были три поля, скопированные из одного
 * ответа в другой, «осталось» в одном месте легко разошлось бы с «осталось»
 * в другом.
 */
@Schema(description = "Квота партий в режиме диверсий")
public record SabotageEntitlementView(

        @Schema(description = "Можно ли начать ещё одну партию с диверсиями",
                example = "true", type = "boolean")
        boolean allowed,

        @Schema(description = "Безлимит: у владельца сервиса и у его друзей квоты нет",
                example = "false", type = "boolean")
        boolean unlimited,

        @Schema(description = "Сколько бесплатных партий осталось; null — при безлимите, "
                + "потому что «осталось 0» и «считать нечего» — разные состояния",
                example = "3", type = "integer", nullable = true)
        Integer remaining) {
}
