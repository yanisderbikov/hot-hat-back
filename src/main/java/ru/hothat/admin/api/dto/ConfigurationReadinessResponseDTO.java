package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Готовность окружения бекенда.
 *
 * <p>Заменяет блок {@code env} из открытого {@code GET /api/health} (A13).
 * Сам по себе он секретов не выдаёт, но перечисляет, какие интеграции у
 * сервиса есть и какие из них не настроены, — это карта для того, кто ищет
 * слабое место, и анонимному она не нужна. Проба живости остаётся открытой и
 * отвечает только «жив».
 */
@Schema(description = "Что настроено в окружении бекенда")
public record ConfigurationReadinessResponseDTO(

        @Schema(description = "Имя службы", example = "hot-hat-back")
        String service,

        @Schema(description = "Версия Java, на которой запущен процесс", example = "21.0.4")
        String javaVersion,

        @Schema(description = "Все перечисленные настройки заданы", example = "true", type = "boolean")
        boolean ready,

        @Schema(description = "Настройки в порядке важности")
        List<ConfigurationKeyView> keys) {
}
