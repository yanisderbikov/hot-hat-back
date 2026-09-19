package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Одна настройка окружения: задана или нет.
 *
 * <p>Значения здесь нет и быть не может — только признак. Ключи вроде
 * {@code JWT_SECRET} и {@code TURN_SECRET} названы поимённо именно потому, что
 * их значения секретны, а знать надо ровно одно: не забыли ли их выставить.
 */
@Schema(description = "Готовность одной настройки окружения")
public record ConfigurationKeyView(

        @Schema(description = "Имя переменной окружения", example = "LIVEKIT_API_SECRET")
        String key,

        @Schema(description = "Значение задано и непустое", example = "true", type = "boolean")
        boolean configured,

        @Schema(description = "Что перестанет работать без неё", example = "Выдача токенов видеосвязи")
        String purpose) {
}
