package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Сохранённая оптимизированная версия мема. */
@Schema(description = "Сохранённая оптимизированная версия мема")
public record OptimizedMemeResponseDTO(

        @Schema(description = "Мем", example = "meme-9f3a1c7b2e54")
        String memeId,

        @Schema(description = "Сколько байт занял ролик в его текстовом виде", example = "1048576",
                type = "integer")
        int bytes,

        @Schema(description = "Тип содержимого, с которым ролик сохранён", example = "video/webm")
        String mime,

        @Schema(description = "Длительность в миллисекундах", example = "5000", type = "integer")
        int durationMs,

        @Schema(description = "Метка версии оптимизатора", example = "mobile-v1")
        String optimizedVersion,

        @Schema(description = "Когда сохранили", example = "1788600000000", type = "integer")
        long optimizedAtMs) {
}
