package ru.hothat.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Состояние одной возможности.
 *
 * <p>Имя возвращается назад тем же значением, что спросили: флаги читаются
 * параллельно ({@code features.js} склеивает запросы), и ответ без имени
 * пришлось бы сопоставлять с запросом по порядку.
 */
@Schema(description = "Включена ли возможность")
public record FeatureFlagResponseDTO(

        @Schema(description = "Имя возможности — то же, что в адресе", example = "bot_enabled",
                allowableValues = {"bot_enabled", "admin_feature"})
        String name,

        @Schema(description = "Включена ли возможность прямо сейчас", example = "false", type = "boolean")
        boolean enabled) {
}
